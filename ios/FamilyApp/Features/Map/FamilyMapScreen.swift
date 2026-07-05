// Family map — the iOS twin of FamilyMapScreen.kt on MapKit: avatar-palette member
// pins, reverse-geocoded legend, my-location centering, permission rationale, and
// visibility cleared on leave (foreground-only v1).
import CoreLocation
import MapKit
import SwiftUI

struct FamilyMapScreen: View {
    @State private var viewModel = FamilyMapViewModel()
    @State private var camera: MapCameraPosition = .automatic
    @State private var showRationale = false
    @State private var placeNames: [String: String] = [:]

    private var isSolo: Bool {
        viewModel.locations.isEmpty
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Map(position: $camera) {
                UserAnnotation()
                ForEach(viewModel.locations, id: \.userId) { location in
                    Annotation(
                        location.displayName,
                        coordinate: CLLocationCoordinate2D(latitude: location.lat, longitude: location.lng)
                    ) {
                        MemberPin(
                            user: viewModel.userProfiles[location.userId],
                            displayName: location.displayName
                        )
                    }
                }
            }
            .mapControls {
                MapCompass()
                MapScaleView()
            }

            VStack(alignment: .trailing, spacing: Spacing.sm) {
                Button {
                    if let coordinate = viewModel.myLocation {
                        withAnimation {
                            camera = .region(MKCoordinateRegion(
                                center: coordinate,
                                latitudinalMeters: 2000,
                                longitudinalMeters: 2000
                            ))
                        }
                    }
                } label: {
                    Image(systemName: "location.fill")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Color.appPrimary)
                        .frame(width: 48, height: 48)
                        .glassCircle()
                }
                .accessibilityLabel("Center on my location")

                if !isSolo {
                    MemberLegend(
                        locations: viewModel.locations,
                        profiles: viewModel.userProfiles,
                        placeNames: placeNames
                    )
                }
            }
            .padding(Spacing.screenEdge)
        }
        .featureTopBar(L("Family Map"))
        .onAppear {
            switch viewModel.authorizationStatus {
            case .notDetermined:
                showRationale = true
            case .authorizedWhenInUse, .authorizedAlways:
                viewModel.startLocationUpdates()
            default:
                break
            }
        }
        .onDisappear {
            viewModel.stopLocationUpdates()
            // Foreground-only v1: leaving the map hides the pin (Android parity).
            viewModel.clearOwnLocation()
        }
        .task(id: viewModel.locations.map(\.userId)) {
            await reverseGeocode()
        }
        .alert("Share your location?", isPresented: $showRationale) {
            Button("Continue") { viewModel.requestPermission() }
            Button("Not now", role: .cancel) {}
        } message: {
            Text(
                // swiftlint:disable:next line_length
                "Your location is shared only with your family, and only while the map is open. You can turn visibility off anytime in Settings."
            )
        }
    }

    /// Reverse-geocodes shared locations to short place names, cached by user id.
    private func reverseGeocode() async {
        for location in viewModel.locations where placeNames[location.userId] == nil {
            guard let request = MKReverseGeocodingRequest(
                location: CLLocation(latitude: location.lat, longitude: location.lng)
            ) else { continue }
            let address = try? await request.mapItems.first?.addressRepresentations
            if let name = address?.cityName ?? address?.regionName {
                placeNames[location.userId] = name
            }
        }
    }
}

/// Avatar pin colored from the shared avatar palette.
private struct MemberPin: View {
    let user: UserModel?
    let displayName: String

    var body: some View {
        let color = Color(argb: (user?.avatarColor).flatMap { $0 != 0 ? $0 : nil }
            ?? FamilyRepository.palette(displayName))
        VStack(spacing: 0) {
            InitialAvatar(
                name: user?.name ?? displayName,
                color: color,
                size: 36,
                avatarUrl: user?.avatarUrl
            )
            .overlay(Circle().strokeBorder(color, lineWidth: 2))
            Triangle()
                .fill(color)
                .frame(width: 12, height: 7)
        }
        .shadow(color: .black.opacity(0.25), radius: 3, y: 1)
    }
}

private struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

/// Legend card listing shared members with place name + last-seen.
private struct MemberLegend: View {
    let locations: [UserLocationModel]
    let profiles: [String: UserModel]
    let placeNames: [String: String]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            ForEach(locations, id: \.userId) { location in
                HStack(spacing: Spacing.md) {
                    if let user = profiles[location.userId] {
                        InitialAvatar(user: user, size: 34)
                    }
                    VStack(alignment: .leading, spacing: 1) {
                        Text(location.displayName)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color.appOnSurface)
                        Text(legendDetail(for: location))
                            .font(.system(size: 12))
                            .foregroundStyle(Color.appCaption)
                    }
                    Spacer(minLength: Spacing.sm)
                    Circle()
                        .fill(isLive(location) ? Color.appSuccess : Palette.staleDot)
                        .frame(width: 8, height: 8)
                }
            }
        }
        .padding(Spacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard(cornerRadius: Radius.row)
    }

    /// "Live" if the location updated within the last 5 minutes.
    private func isLive(_ location: UserLocationModel) -> Bool {
        guard let updatedAt = location.updatedAt,
              let updated = ISO8601DateFormatter().date(from: updatedAt) else { return false }
        return Date().timeIntervalSince(updated) < 300
    }

    private func legendDetail(for location: UserLocationModel) -> String {
        let lastSeen = formatLastSeen(location.updatedAt, locale: appLocale)
        if let place = placeNames[location.userId] {
            return "\(place) · \(lastSeen)"
        }
        return lastSeen
    }
}
