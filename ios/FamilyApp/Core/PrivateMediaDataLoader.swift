import Foundation
import Nuke

/// The signed object capability is short-lived and is never stored in URLCache.
final class PrivateMediaDataLoader: DataLoading, @unchecked Sendable {
    static let session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        return URLSession(configuration: configuration)
    }()
    private let resolver: MediaURLResolver

    init(resolver: MediaURLResolver) {
        self.resolver = resolver
    }

    func loadData(
        with request: URLRequest,
        didReceiveData: @escaping (Data, URLResponse) -> Void,
        completion: @escaping (Error?) -> Void
    ) -> any Cancellable {
        let task = Task {
            do {
                guard let url = request.url else { throw MediaAccessError.invalidURL }
                let (data, response) = try await Self.download(url: url, resolver: resolver)
                try Task.checkCancellation()
                didReceiveData(data, response)
                completion(nil)
            } catch {
                completion(error)
            }
        }
        return MediaLoadTask(task: task)
    }

    static func download(
        url: URL, resolver: MediaURLResolver, timeout: TimeInterval = 15,
        session: URLSession = PrivateMediaDataLoader.session
    ) async throws -> (Data, URLResponse) {
        let protected = try resolver.reference(url) != nil
        let account = resolver.accountID()
        let readable = try await resolver.resolve(url)
        var request = URLRequest(url: readable)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = timeout
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode)
        else { throw MediaAccessError.invalidResponse }
        if protected, resolver.accountID() != account { throw MediaAccessError.accountChanged }
        try Task.checkCancellation()
        return (data, response)
    }
}

private final class MediaLoadTask: Cancellable, @unchecked Sendable {
    private let task: Task<Void, Never>

    init(task: Task<Void, Never>) {
        self.task = task
    }

    func cancel() {
        task.cancel()
    }
}

/// Protected requests bypass both cache layers, including legacy public-URL entries.
final class PrivateMediaPipelineDelegate: ImagePipelineDelegate, @unchecked Sendable {
    private let resolver: MediaURLResolver
    private let loader: PrivateMediaDataLoader

    init(resolver: MediaURLResolver) {
        self.resolver = resolver
        loader = PrivateMediaDataLoader(resolver: resolver)
    }

    func dataLoader(for request: ImageRequest, pipeline: ImagePipeline) -> any DataLoading {
        isProtected(request) ? loader : pipeline.configuration.dataLoader
    }

    func imageCache(for request: ImageRequest, pipeline: ImagePipeline) -> (any ImageCaching)? {
        isProtected(request) ? nil : pipeline.configuration.imageCache
    }

    func dataCache(for request: ImageRequest, pipeline: ImagePipeline) -> (any DataCaching)? {
        isProtected(request) ? nil : pipeline.configuration.dataCache
    }

    private func isProtected(_ request: ImageRequest) -> Bool {
        guard let url = request.url else { return false }
        do {
            return try resolver.reference(url) != nil
        } catch {
            // Malformed storage references must reach the rejecting loader, never an old cache entry.
            return true
        }
    }
}
