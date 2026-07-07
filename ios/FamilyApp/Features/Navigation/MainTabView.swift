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
    @State private var calendarViewModel = CalendarViewModel()
    @State private var birthdayViewModel = BirthdayViewModel()
    @State private var wishlistViewModel = WishlistViewModel()
    @State private var familyViewModel = FamilyViewModel()
    @State private var profileViewModel = ProfileViewModel()
    /// Shared between the chat list and open thread — deletes in the thread must
    /// reflect in the list on pop-back (see CLAUDE.md).
    @State private var chatViewModel = ChatViewModel()
    /// Set when a Google sign-up needs to complete their phone/birthday.
    @State private var completionUser: UserModel?

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
                CalendarScreen(viewModel: calendarViewModel)
                    .navigationDestination(for: Route.self) { destination(for: $0) }
            }
            .tabItem { Label("Calendar", systemImage: "calendar") }
            .tag(Tab.calendar)

            NavigationStack(path: $chatPath) {
                ChatScreen(viewModel: chatViewModel) { chatPath.append(.chatDetail(conversationId: $0)) }
                    .navigationDestination(for: Route.self) { destination(for: $0) }
            }
            .tabItem { Label("Chat", systemImage: "bubble.left.and.bubble.right.fill") }
            .tag(Tab.chat)
            .badge(chatViewModel.totalUnread)

            NavigationStack(path: $familyPath) {
                FamilyScreen(viewModel: familyViewModel)
                    .navigationDestination(for: Route.self) { destination(for: $0) }
            }
            .tabItem { Label("Family", systemImage: "person.3.fill") }
            .tag(Tab.family)

            NavigationStack(path: $profilePath) {
                ProfileScreen(
                    viewModel: profileViewModel,
                    onEdit: { profilePath.append(.profileEdit) },
                    onSettings: { profilePath.append(.settings) }
                )
                .navigationDestination(for: Route.self) { destination(for: $0) }
            }
            .tabItem { Label("Profile", systemImage: "person.crop.circle.fill") }
            .tag(Tab.profile)
        }
        .tint(Color.appPrimary)
        .task {
            LocationSharingService.shared.startIfEnabled()
            await checkProfileCompletion()
        }
        .sheet(item: $completionUser) { user in
            ProfileCompletionSheet(
                onSave: { mobile, birthday in
                    Task {
                        await FamilyRepository.shared.updateProfile(
                            userId: user.id,
                            update: ProfileUpdate(
                                name: user.name, email: user.email,
                                birthday: birthday, mobile: mobile, avatarUrl: user.avatarUrl
                            )
                        )
                        UserDefaults.standard.set(true, forKey: "profile_completed_\(user.id)")
                        profileViewModel.refresh()
                    }
                },
                onSkip: { UserDefaults.standard.set(true, forKey: "profile_completed_\(user.id)") }
            )
        }
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

    /// One-time prompt for Google sign-ups (email registration already collects these).
    private func checkProfileCompletion() async {
        guard let userId = FamilyRepository.shared.session.currentUserId,
              !UserDefaults.standard.bool(forKey: "profile_completed_\(userId)") else { return }
        let provider = SupabaseClientProvider.client.auth.currentSession?
            .user.appMetadata["provider"]?.stringValue
        guard provider == "google" else { return }
        if let user = await FamilyRepository.shared.getUser(userId), user.birthday.isEmpty {
            completionUser = user
        }
    }

    /// Route → screen. Placeholders are swapped out feature phase by feature phase.
    @ViewBuilder
    private func destination(for route: Route) -> some View {
        switch route {
        case .shopping:
            ShoppingScreen(viewModel: shoppingViewModel) { homePath.append(.shoppingDetail(listId: $0)) }
        case let .shoppingDetail(listId):
            ShoppingDetailScreen(listId: listId, viewModel: shoppingViewModel)
        case .meal:
            MealScreen(viewModel: mealViewModel) { homePath.append(.mealDetail(planId: $0)) }
        case let .mealDetail(planId):
            MealDetailScreen(planId: planId, viewModel: mealViewModel)
        case .birthday:
            BirthdayScreen(viewModel: birthdayViewModel)
        case .wishlist:
            WishlistScreen(viewModel: wishlistViewModel) { homePath.append(.wishlistDetail(wishlistId: $0)) }
        case let .wishlistDetail(wishlistId):
            WishlistDetailScreen(wishlistId: wishlistId, viewModel: wishlistViewModel)
        case let .chatDetail(conversationId):
            ConversationScreen(conversationId: conversationId, viewModel: chatViewModel)
                .onChange(of: chatViewModel.navigateToConversation) { _, newId in
                    // 1:1 promoted to a group — swap the thread.
                    guard let newId else { return }
                    chatViewModel.navigateToConversation = nil
                    chatPath = [.chatDetail(conversationId: newId)]
                }
        case .profileEdit:
            ProfileEditScreen(viewModel: profileViewModel)
        case .settings:
            SettingsScreen()
        case .familyMap:
            FamilyMapScreen()
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
