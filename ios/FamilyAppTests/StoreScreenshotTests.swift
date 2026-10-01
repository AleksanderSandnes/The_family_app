@testable import FamilyApp

// App Store screenshots of key screens from the fictional Nordmann family (MockRepository
// fixtures — no backend, no real data). Each image is attached to the test result as
// "store-<name>"; .github/workflows/ios.yml exports them into the ios-store-screenshots
// artifact. iPhone: 1320 x 2868 pixels. iPad 13": 2064 x 2752 pixels.
import SwiftUI
import UIKit
import XCTest

@MainActor
final class StoreScreenshotTests: XCTestCase {
    private var size = CGSize(width: 440, height: 956)
    private var scale: CGFloat = 3
    private var prefix = "store"

    override func setUp() async throws {
        try await super.setUp()
        StubSupabase.install()
    }

    override func tearDown() async throws {
        StubSupabase.uninstall()
        try await super.tearDown()
    }

    func testCaptureStoreScreenshots() async throws {
        try await captureScreens()
    }

    func testCaptureIPadStoreScreenshots() async throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .pad, "Requires an iPad simulator")
        size = CGSize(width: 1032, height: 1376)
        scale = 2
        prefix = "store-ipad13"
        try await captureScreens()
    }

    private func captureScreens() async throws {
        let mock = DemoFamily.mock()
        let home = HomeViewModel(repo: mock)
        let chat = ChatViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        let shopping = ShoppingViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        let calendar = CalendarViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        let meals = MealViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        let wishes = WishlistViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        await ScreenRenderer.settle()

        try await capture("01-home") {
            NavigationStack {
                HomeScreen(viewModel: home, onOpen: { _ in }, onOpenCalendarTab: {}, onOpenFamily: {})
            }
        }
        try await capture("02-chat") {
            NavigationStack { ConversationScreen(conversationId: "chat-1", viewModel: chat) }
        }
        try await capture("03-shopping") {
            NavigationStack { ShoppingDetailScreen(listId: "l1", viewModel: shopping) }
        }
        try await capture("04-calendar") {
            NavigationStack { CalendarScreen(viewModel: calendar) }
        }
        try await capture("05-meals") {
            NavigationStack { MealDetailScreen(planId: "plan-1", viewModel: meals) }
        }
        try await capture("06-wishlist") {
            NavigationStack { WishlistDetailScreen(wishlistId: "w1", viewModel: wishes) }
        }
    }

    private func capture(_ name: String, @ViewBuilder _ content: () -> some View) async throws {
        let host = UIHostingController(rootView: content().environment(\.locale, Locale(identifier: "en")))
        host.overrideUserInterfaceStyle = .light
        let window = UIWindow(frame: CGRect(origin: .zero, size: size))
        window.rootViewController = host
        window.makeKeyAndVisible()
        for _ in 0..<10 {
            host.view.layoutIfNeeded()
            try await Task.sleep(nanoseconds: 50000000)
        }
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        let image = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            _ = host.view.drawHierarchy(in: CGRect(origin: .zero, size: size), afterScreenUpdates: true)
        }
        window.isHidden = true
        let png = try XCTUnwrap(image.pngData())
        XCTAssertEqual(image.size.width * image.scale, size.width * scale)
        XCTAssertEqual(image.size.height * image.scale, size.height * scale)
        let attachment = XCTAttachment(data: png, uniformTypeIdentifier: "public.png")
        attachment.name = "\(prefix)-\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
