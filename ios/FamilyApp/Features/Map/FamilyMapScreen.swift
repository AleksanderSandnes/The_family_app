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

    private var isSolo: Bool { viewModel.locations.isEmpty }

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
                        .font(.system(size: 18))
                        .foregroundStyle(Color.appOnPrimary)
                        .frame(width: 48, height: 48)
                        .background(Color.appPrimary)
                        .clipShape(Circle())
                        .shadow(color: .black.opacity(0.2), radius: 4, y: 2)
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
        .featureTopBar("Family Map")
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
            Text("Your location is shared only with your family, and only while the map is open. You can turn visibility off anytime in Settings.")
        }
    }

    /// Reverse-geocodes shared locations to short place names, cached by user id.
    private func reverseGeocode() async {
        let geocoder = CLGeocoder()
        for location in viewModel.locations where placeNames[location.userId] == nil {
            let place = try? await geocoder.reverseGeocodeLocation(
                CLLocation(latitude: location.lat, longitude: location.lng)
            ).first
            if let name = place?.locality ?? place?.subAdministrativeArea {
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
        VStack(alignment: .leading, spacing: Spacing.sm) {
            ForEach(locations, id: \.userId) { location in
                HStack(spacing: Spacing.sm) {
                    if let user = profiles[location.userId] {
                        InitialAvatar(user: user, size: 28)
                    }
                    VStack(alignment: .leading, spacing: 0) {
                        Text(location.displayName)
                            .font(.labelMedium.weight(.semibold))
                            .foregroundStyle(Color.appOnSurface)
                        Text(legendDetail(for: location))
                            .font(.labelMedium)
                            .foregroundStyle(Color.appOnSurfaceVariant)
                    }
                }
            }
        }
        .padding(Spacing.md)
        .background(.thinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: Radius.small, style: .continuous))
    }

    private func legendDetail(for location: UserLocationModel) -> String {
        let lastSeen = formatLastSeen(location.updatedAt)
        if let place = placeNames[location.userId] {
            return "\(place) · \(lastSeen)"
        }
        return lastSeen
    }
}
