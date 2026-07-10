// App-scoped location sharing — publishes the user's location to `user_locations` while
// sharing is enabled, including in the BACKGROUND when the user grants "Always" access
// (UIBackgroundModes: location). The settings toggle drives enable/disable and reflects
// the real system authorization status. Foreground map viewing uses FamilyMapViewModel.
import CoreLocation
import Foundation
import Observation
import Supabase

private let publishIntervalSeconds: Double = 30

@Observable
@MainActor
final class LocationSharingService: NSObject, CLLocationManagerDelegate {
    static let shared = LocationSharingService()

    private(set) var authorizationStatus: CLAuthorizationStatus

    private let manager = CLLocationManager()
    private let repo = FamilyRepository.shared
    private var client: SupabaseClient {
        SupabaseClientProvider.client
    }

    private var lastPublish = Date.distantPast

    override private init() {
        authorizationStatus = manager.authorizationStatus
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    /// Whether sharing can currently be on (permission not denied/restricted).
    var permissionAllowsSharing: Bool {
        authorizationStatus != .denied && authorizationStatus != .restricted
    }

    var isSharing: Bool {
        repo.session.locationVisible && permissionAllowsSharing
    }

    /// User turned the toggle ON — request Always (for background) then start publishing.
    func enableSharing() {
        repo.session.setLocationVisible(true)
        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestAlwaysAuthorization()
        case .authorizedWhenInUse:
            manager.requestAlwaysAuthorization()
            start()
        case .authorizedAlways:
            start()
        default:
            break // denied/restricted — the toggle reflects this and stays off.
        }
    }

    /// User turned the toggle OFF — stop publishing and hide the pin.
    func disableSharing() {
        repo.session.setLocationVisible(false)
        stop()
        Task { await hidePin() }
    }

    /// Called on launch — resume background sharing if the user left it enabled.
    func startIfEnabled() {
        guard repo.session.locationVisible, permissionAllowsSharing,
              manager.authorizationStatus != .notDetermined else { return }
        start()
    }

    private func start() {
        // Background updates require "Always" + the location background mode.
        if manager.authorizationStatus == .authorizedAlways {
            manager.allowsBackgroundLocationUpdates = true
            manager.pausesLocationUpdatesAutomatically = false
        }
        manager.startUpdatingLocation()
    }

    private func stop() {
        manager.allowsBackgroundLocationUpdates = false
        manager.stopUpdatingLocation()
    }

    private func hidePin() async {
        guard let userId = repo.session.currentUserId else { return }
        _ = try? await client.from("user_locations")
            .update(["visible": AnyJSON.bool(false)])
            .eq("user_id", value: userId)
            .execute()
    }

    private func publish(_ coordinate: CLLocationCoordinate2D) async {
        guard repo.session.locationVisible,
              let userId = repo.session.currentUserId,
              let user = await repo.getUser(userId) else { return }
        _ = try? await client.from("user_locations").upsert([
            "user_id": AnyJSON.string(userId),
            "family_id": user.familyId.map { AnyJSON.string($0) } ?? .null,
            "lat": .double(coordinate.latitude),
            "lng": .double(coordinate.longitude),
            "display_name": .string(user.name),
            "visible": .bool(true),
            "updated_at": .string(isoNow()),
        ]).execute()
    }

    // MARK: - CLLocationManagerDelegate

    nonisolated func locationManager(
        _ manager: CLLocationManager,
        didUpdateLocations locations: [CLLocation]
    ) {
        guard let location = locations.last else { return }
        Task { @MainActor in
            if Date().timeIntervalSince(lastPublish) >= publishIntervalSeconds {
                lastPublish = Date()
                await publish(location.coordinate)
            }
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            authorizationStatus = status
            if repo.session.locationVisible,
               status == .authorizedAlways || status == .authorizedWhenInUse {
                start()
            } else if status == .denied || status == .restricted {
                stop()
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {}
}
