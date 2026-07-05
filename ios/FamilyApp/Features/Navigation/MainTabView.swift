// Main signed-in shell — the iOS twin of MainFlow in AppNavHost.kt.
// 5 tabs, each with its own NavigationStack; feature view models are hoisted here
// (Android: Activity-scoped hoisting in MainFlow) so they survive tab switches and
// start loading immediately on login. Chat list + detail will share one view model.
import SwiftUI

struct MainTabView: View {
    @Environment(DeepLinkRouter.self) private var deepLinks

    @State private var selectedTab: Tab = .home
    @State private var homePath: [Route] = []
    @State private var calendarPath: [Route] = []
    @State private var chatPath: [Route] = []
    @State private var familyPath: [Route] = []
    @State private var profilePath: [Route] = []

    // Hoisted feature view models — created once for the signed-in session.
    @State private var homeViewModel = HomeViewModel()
    @State private var shoppingViewModel = ShoppingViewModel()
    @State private var mealViewModel = MealViewModel()

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack(path: $homePath) {
                HomeScreen(
                    viewModel: homeViewModel,
                    onOpen: { homePath.append($0) },
                    onOpenCalendarTab: { selectedTab = .calendar },
                    onOpenFamily: { selectedTab = .family }
                )
                .navigationDestination(for: Route.self) { destination(for: $0) }
            }
            .tabItem { Label("Home", systemImage: "house.fill") }
            .tag(Tab.home)

            NavigationStack(path: $calendarPath) {
                PlaceholderScreen(title: "Calendar")
                    .navigationDestination(for: Route.self) { destination(for: $0) }
            }
            .tabItem { Label("Calendar", systemImage: "calendar") }
            .tag(Tab.calendar)

            NavigationStack(path: $chatPath) {
                PlaceholderScreen(title: "Chat")
                    .navigationDestination(for: Route.self) { destination(for: $0) }
            }
            .tabItem { Label("Chat", systemImage: "bubble.left.and.bubble.right.fill") }
            .tag(Tab.chat)

            NavigationStack(path: $familyPath) {
                PlaceholderScreen(title: "Family")
                    .navigationDestination(for: Route.self) { destination(for: $0) }
            }
            .tabItem { Label("Family", systemImage: "person.3.fill") }
            .tag(Tab.family)

            NavigationStack(path: $profilePath) {
                PlaceholderScreen(title: "Profile")
                    .navigationDestination(for: Route.self) { destination(for: $0) }
            }
            .tabItem { Label("Profile", systemImage: "person.crop.circle.fill") }
            .tag(Tab.profile)
        }
        .tint(Color.appPrimary)
        .onChange(of: deepLinks.pendingConversationId) { _, conversationId in
            // Push tap / chat link: land on the chat tab and open the thread.
            guard let conversationId else { return }
            selectedTab = .chat
            chatPath = [.chatDetail(conversationId: conversationId)]
            deepLinks.pendingConversationId = nil
        }
        .onChange(of: FamilyRepository.shared.pendingJoinCode) { _, code in
            // Invite deep link routes the user to Family, which opens the join flow.
            if code != nil { selectedTab = .family }
        }
    }

    /// Route → screen. Placeholders are swapped out feature phase by feature phase.
    @ViewBuilder
    private func destination(for route: Route) -> some View {
        switch route {
        case .shopping:
            ShoppingScreen(viewModel: shoppingViewModel) { homePath.append(.shoppingDetail(listId: $0)) }
        case .shoppingDetail(let listId):
            ShoppingDetailScreen(listId: listId, viewModel: shoppingViewModel)
        case .meal:
            MealScreen(viewModel: mealViewModel) { homePath.append(.mealDetail(planId: $0)) }
        case .mealDetail(let planId):
            MealDetailScreen(planId: planId, viewModel: mealViewModel)
        case .birthday:
            PlaceholderScreen(title: "Birthdays")
        case .wishlist:
            PlaceholderScreen(title: "Wishlists")
        case .wishlistDetail(let wishlistId):
            PlaceholderScreen(title: "Wishlist \(wishlistId)")
        case .chatDetail(let conversationId):
            PlaceholderScreen(title: "Conversation \(conversationId)")
        case .profileEdit:
            PlaceholderScreen(title: "Edit profile")
        case .settings:
            PlaceholderScreen(title: "Settings")
        case .familyMap:
            PlaceholderScreen(title: "Family Map")
        }
    }
}

/// Temporary stand-in for screens arriving in later phases.
struct PlaceholderScreen: View {
    let title: String

    var body: some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: "hammer.fill")
                .font(.system(size: 32))
                .foregroundStyle(Color.appOnSurfaceVariant)
            Text(title)
                .font(.headlineSmall)
                .foregroundStyle(Color.appOnBackground)
            Text("This screen arrives in a later phase.")
                .font(.bodyMedium)
                .foregroundStyle(Color.appOnSurfaceVariant)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.appBackground)
        .featureTopBar(title)
    }
}
