// Family map view model — the iOS twin of FamilyMapViewModel.kt: publishes the own
// location every 30 s while the map is open (foreground-only v1, matching Android's
// Play-Store build), streams family member locations via realtime.
import CoreLocation
import Foundation
import Observation
import Supabase

private let publishIntervalSeconds: Double = 30

@Observable
@MainActor
final class FamilyMapViewModel: NSObject, CLLocationManagerDelegate {
    private(set) var myLocation: CLLocationCoordinate2D?
    private(set) var locations: [UserLocationModel] = []
    private(set) var userProfiles: [String: UserModel] = [:]
    private(set) var isLoading = false
    private(set) var authorizationStatus: CLAuthorizationStatus = .notDetermined

    var currentUserId: String? {
        repo.session.currentUserId
    }

    private let repo: FamilyRepositoryProtocol
    private let observer: RealtimeObserving
    private var currentFamilyId: String?

    private let locationManager = CLLocationManager()
    private var publishTask: Task<Void, Never>?
    private var lastPublish = Date.distantPast

    init(
        repo: FamilyRepositoryProtocol? = nil,
        realtime: @MainActor () -> RealtimeObserving = { RealtimeObserver() }
    ) {
        self.repo = repo ?? FamilyRepository.shared
        observer = realtime()
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        authorizationStatus = locationManager.authorizationStatus
        Task { await load() }
    }

    func requestPermission() {
        locationManager.requestWhenInUseAuthorization()
    }

    private func load() async {
        guard let userId = repo.session.currentUserId,
              let user = await repo.getUser(userId) else { return }
        currentFamilyId = user.familyId
        await loadLocations()
        guard let familyId = user.familyId else { return }
        let members = await repo.getFamilyMembers(familyId: familyId)
        userProfiles = Dictionary(members.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        observer.start(
            table: "user_locations",
            scope: familyId,
            filter: .eq("family_id", value: familyId)
        ) { [weak self] in
            await self?.loadLocations()
        }
    }

    private func loadLocations() async {
        guard let familyId = currentFamilyId, let myId = repo.session.currentUserId else { return }
        isLoading = true
        defer { isLoading = false }
        let fetched = await (try? repo.fetchUserLocations(familyId: familyId)) ?? locations
        locations = fetched.filter { $0.userId != myId }
    }

    // MARK: - Own location publishing (30 s while the map is open)

    func startLocationUpdates() {
        guard publishTask == nil else { return }
        locationManager.startUpdatingLocation()
        publishTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(publishIntervalSeconds))
                guard let self, let coordinate = myLocation else { continue }
                await publishLocation(lat: coordinate.latitude, lng: coordinate.longitude)
            }
        }
    }

    func stopLocationUpdates() {
        locationManager.stopUpdatingLocation()
        publishTask?.cancel()
        publishTask = nil
    }

    /// Hide the pin when the map screen goes away — UNLESS the user enabled persistent
    /// background sharing in settings, in which case LocationSharingService keeps it live.
    func clearOwnLocation() {
        guard !repo.session.locationVisible else { return }
        Task {
            guard let userId = repo.session.currentUserId else { return }
            await repo.clearUserLocationVisibility(userId: userId)
        }
    }

    private func publishLocation(lat: Double, lng: Double) async {
        guard let userId = repo.session.currentUserId,
              let user = await repo.getUser(userId) else { return }
        var location = UserLocationModel()
        location.userId = userId
        location.familyId = user.familyId
        location.lat = lat
        location.lng = lng
        location.displayName = user.name
        location.visible = repo.session.locationVisible
        await repo.upsertUserLocation(location)
    }

    // MARK: - CLLocationManagerDelegate

    nonisolated func locationManager(
        _ manager: CLLocationManager,
        didUpdateLocations newLocations: [CLLocation]
    ) {
        guard let location = newLocations.last else { return }
        Task { @MainActor in
            let isFirstFix = myLocation == nil
            myLocation = location.coordinate
            // Publish immediately on the first fix, then the 30 s timer takes over.
            if isFirstFix || Date().timeIntervalSince(lastPublish) >= publishIntervalSeconds {
                lastPublish = Date()
                await publishLocation(
                    lat: location.coordinate.latitude,
                    lng: location.coordinate.longitude
                )
            }
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            authorizationStatus = status
            if status == .authorizedWhenInUse || status == .authorizedAlways {
                startLocationUpdates()
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {}
}

/// Last-seen label — mirrors formatLastSeen in FamilyMapScreen.kt. Pass `appLocale` so
/// the relative label and fallback date render in the app language.
func formatLastSeen(
    _ updatedAt: String?,
    nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
    locale: Locale = Locale(identifier: "en_US_POSIX")
) -> String {
    guard let updatedAt else { return L("Unknown", locale: locale) }
    guard let instant = parseInstantMs(updatedAt) else {
        return L("Location shared", locale: locale)
    }
    let seconds = (nowMs - instant) / 1000
    switch true {
    case seconds < 60: return L("Just now", locale: locale) // also clock-skew futures
    case seconds < 3600: return L("\(Int(seconds / 60)) min ago", locale: locale)
    case seconds < 86400: return L("\(Int(seconds / 3600)) hours ago", locale: locale)
    default:
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy"
        formatter.locale = locale
        return formatter.string(from: Date(timeIntervalSince1970: Double(instant) / 1000))
    }
}
