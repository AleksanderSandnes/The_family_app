// Supabase Storage uploads with the path rules enforced IN ONE PLACE so call sites
// can't get the dual-ID rule wrong:
//
//   avatars/{auth_uid}/{filename}   ← auth UUID, NOT public.users.id! The bucket RLS
//                                     checks (storage.foldername(name))[1] = auth.uid();
//                                     using the app user id fails silently.
//   group-images, wish-images       ← same auth-uid-first convention
//   chat-media/{conversationId}/{auth_uid}/{filename}
import Foundation
import Supabase

enum StorageService {
    private static var client: SupabaseClient { SupabaseClientProvider.client }

    /// The Supabase Auth UUID (auth_id) — the ONLY id allowed in storage paths.
    private static func authUid() throws -> String {
        guard let id = client.auth.currentSession?.user.id.uuidString.lowercased() else {
            throw RepositoryError.notAuthenticated
        }
        return id
    }

    @discardableResult
    static func uploadAvatar(data: Data, filename: String) async throws -> String {
        try await upload(bucket: "avatars", path: "\(try authUid())/\(filename)", data: data)
    }

    @discardableResult
    static func uploadGroupImage(data: Data, filename: String) async throws -> String {
        try await upload(bucket: "group-images", path: "\(try authUid())/\(filename)", data: data)
    }

    /// wish-images allows any authenticated write; Android's path convention is the APP
    /// user id (public.users.id) + timestamp — kept identical for cross-platform parity.
    @discardableResult
    static func uploadWishImage(data: Data, appUserId: String, filename: String) async throws -> String {
        try await upload(bucket: "wish-images", path: "\(appUserId)/\(filename)", data: data)
    }

    @discardableResult
    static func uploadChatMedia(
        conversationId: String,
        data: Data,
        filename: String
    ) async throws -> String {
        try await upload(
            bucket: "chat-media",
            path: "\(conversationId)/\(try authUid())/\(filename)",
            data: data
        )
    }

    /// Uploads (upsert) and returns the public URL — mirrors Android's upload + publicUrl.
    private static func upload(bucket: String, path: String, data: Data) async throws -> String {
        let storage = client.storage.from(bucket)
        try await storage.upload(path, data: data, options: FileOptions(upsert: true))
        return try storage.getPublicURL(path: path).absoluteString
    }
}
