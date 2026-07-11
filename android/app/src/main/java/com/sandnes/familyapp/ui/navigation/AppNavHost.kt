@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.sandnes.familyapp.R
import com.sandnes.familyapp.ui.auth.LoginScreen
import com.sandnes.familyapp.ui.auth.RegisterScreen
import com.sandnes.familyapp.ui.birthday.BirthdayScreen
import com.sandnes.familyapp.ui.birthday.BirthdayViewModel
import com.sandnes.familyapp.ui.calendar.CalendarScreen
import com.sandnes.familyapp.ui.calendar.CalendarViewModel
import com.sandnes.familyapp.ui.chat.ChatScreen
import com.sandnes.familyapp.ui.chat.ChatViewModel
import com.sandnes.familyapp.ui.chat.ConversationScreen
import com.sandnes.familyapp.ui.family.FamilyScreen
import com.sandnes.familyapp.ui.family.FamilyViewModel
import com.sandnes.familyapp.ui.home.HomeScreen
import com.sandnes.familyapp.ui.map.FamilyMapScreen
import com.sandnes.familyapp.ui.meal.MealDetailScreen
import com.sandnes.familyapp.ui.meal.MealScreen
import com.sandnes.familyapp.ui.meal.MealViewModel
import com.sandnes.familyapp.ui.onboarding.PermissionsOnboardingScreen
import com.sandnes.familyapp.ui.profile.ProfileEditScreen
import com.sandnes.familyapp.ui.profile.ProfileScreen
import com.sandnes.familyapp.ui.settings.SettingsScreen
import com.sandnes.familyapp.ui.shopping.ShoppingDetailScreen
import com.sandnes.familyapp.ui.shopping.ShoppingScreen
import com.sandnes.familyapp.ui.shopping.ShoppingViewModel
import com.sandnes.familyapp.ui.theme.AmbientBackground
import com.sandnes.familyapp.ui.theme.Radius
import com.sandnes.familyapp.ui.theme.Spacing
import com.sandnes.familyapp.ui.theme.glassBar
import com.sandnes.familyapp.ui.theme.glassSource
import com.sandnes.familyapp.ui.wishlist.WishlistDetailScreen
import com.sandnes.familyapp.ui.wishlist.WishlistScreen
import com.sandnes.familyapp.ui.wishlist.WishlistViewModel

private data class BottomDest(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

// Tab-bar IA mirrors iOS (MainTabView): Home · Shopping · Chat · Calendar · Profile.
// Family, Family Map, Wishlists, Meals, Birthdays and Settings are pushed routes reached
// from the Home dashboard / Profile, not tabs.
private val bottomDestinations =
    listOf(
        BottomDest(Routes.HOME, R.string.home, Icons.Filled.Home),
        BottomDest(Routes.SHOPPING, R.string.shopping, Icons.Filled.ShoppingCart),
        BottomDest(Routes.CHAT, R.string.chat, Icons.AutoMirrored.Filled.Chat),
        BottomDest(Routes.CALENDAR, R.string.calendar, Icons.Filled.CalendarMonth),
        BottomDest(Routes.PROFILE, R.string.profile, Icons.Filled.Person),
    )

@Composable
fun FamilyApp(rootViewModel: RootViewModel = hiltViewModel()) {
    val gate by rootViewModel.gate.collectAsStateWithLifecycle()
    when (gate) {
        AuthGate.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        AuthGate.SignedOut -> AuthFlow()
        AuthGate.NeedsPermissions ->
            PermissionsOnboardingScreen(
                onComplete = { rootViewModel.completePermissionsOnboarding() },
            )
        AuthGate.SignedIn -> MainFlow()
    }
}

@Composable
private fun AuthFlow() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onAuthenticated = { /* RootViewModel reacts to session change */ },
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onAuthenticated = { },
                onNavigateToLogin = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun MainFlow() {
    // Hoist feature ViewModels to MainFlow scope (Activity-scoped).
    // init{} fires immediately on login — data loads in the background before user taps any screen.
    val shoppingVm: ShoppingViewModel = hiltViewModel()
    val birthdayVm: BirthdayViewModel = hiltViewModel()
    val wishlistVm: WishlistViewModel = hiltViewModel()
    val mealVm: MealViewModel = hiltViewModel()
    val calendarVm: CalendarViewModel = hiltViewModel()
    // Shared so the list (ChatScreen) and detail (ConversationScreen) use the SAME
    // instance — a delete in the detail screen must reflect in the list on pop-back.
    val chatVm: ChatViewModel = hiltViewModel()
    val familyVm: FamilyViewModel = hiltViewModel()

    val chatUnread by chatVm.totalUnread.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // iOS keeps the floating tab bar on every pushed screen except the conversation view
    // (full-screen chat with its own composer). Mirror that.
    val showBottomBar = currentRoute != null && currentRoute != Routes.CHAT_DETAIL

    // An invite deep link (familyapp://join?code=…) routes the user to Family,
    // which opens the join flow pre-filled with the code.
    val pendingJoin by familyVm.pendingJoinCode.collectAsStateWithLifecycle()
    LaunchedEffect(pendingJoin) {
        if (pendingJoin != null) {
            navController.navigate(Routes.FAMILY) { launchSingleTop = true }
        }
    }

    // A wishlist share link (familyapp://wishlist?token=…) redeems cross-family access to that
    // one wishlist, then opens the wishlist screen (it appears under "Shared with me").
    val pendingWishlistToken by wishlistVm.pendingShareToken.collectAsStateWithLifecycle()
    LaunchedEffect(pendingWishlistToken) {
        pendingWishlistToken?.let { token ->
            wishlistVm.redeemShareToken(token)
            wishlistVm.consumePendingShareToken()
            navController.navigate(Routes.WISHLIST) { launchSingleTop = true }
        }
    }

    AmbientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets =
                WindowInsets.systemBars.only(
                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                ),
            bottomBar = {
                if (showBottomBar) {
                    // Floating pill tab bar, inset from the screen edges (mirrors the iOS
                    // Liquid Glass tab bar rather than a full-width docked NavigationBar).
                    NavigationBar(
                        containerColor = Color.Transparent,
                        windowInsets = WindowInsets(0),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                                .glassBar(Radius.tabBar)
                                // Inner inset so the first/last selection pills don't touch
                                // the rounded ends of the floating bar (mirrors iOS).
                                .padding(horizontal = Spacing.sm),
                    ) {
                        bottomDestinations.forEach { dest ->
                            val isHome = dest.route == Routes.HOME
                            val isChatDest = dest.route == Routes.CHAT
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = !isHome
                                        }
                                        launchSingleTop = true
                                        restoreState = !isHome
                                    }
                                },
                                icon = {
                                    if (isChatDest) {
                                        BadgedBox(
                                            badge = {
                                                if (chatUnread > 0) {
                                                    Badge(containerColor = Color(0xFFE53935)) {
                                                        Text(
                                                            text = if (chatUnread > 9) "9+" else chatUnread.toString(),
                                                            color = Color.White,
                                                            style = MaterialTheme.typography.labelSmall,
                                                        )
                                                    }
                                                }
                                            },
                                        ) {
                                            Icon(dest.icon, contentDescription = stringResource(dest.labelRes))
                                        }
                                    } else {
                                        Icon(dest.icon, contentDescription = stringResource(dest.labelRes))
                                    }
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        // Translucent glassy pill rather than a solid container
                                        // (approximates the iOS liquid-glass selected tab).
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                // Consume the scaffold insets so each screen's own Scaffold doesn't re-apply the
                // bottom navigation-bar inset (which doubled the gap below FABs above the nav bar).
                modifier = Modifier.padding(padding).consumeWindowInsets(padding).glassSource(),
                // Default: iOS-style horizontal slide for detail/feature screens
                enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
                exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(200)) },
                popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300)) },
                popExitTransition = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)) },
            ) {
                // Bottom-tab destinations: crossfade only (no slide between sibling screens)
                composable(
                    Routes.HOME,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(200)) },
                ) {
                    HomeScreen(
                        onOpen = { route -> navController.navigate(route) },
                        onOpenFamily = { navController.navigate(Routes.FAMILY) },
                    )
                }
                composable(
                    Routes.CALENDAR,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(200)) },
                ) { CalendarScreen(viewModel = calendarVm) }
                composable(
                    Routes.CHAT,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(200)) },
                ) { ChatScreen(onOpen = { id -> navController.navigate(Routes.chatDetail(id)) }, viewModel = chatVm) }
                composable(
                    Routes.PROFILE,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(200)) },
                ) {
                    ProfileScreen(
                        onEdit = { navController.navigate(Routes.PROFILE_EDIT) },
                        onSettings = { navController.navigate(Routes.SETTINGS) },
                        onSignedOut = { /* RootViewModel reacts */ },
                    )
                }

                // Shopping is now a bottom-tab destination (crossfade, no back arrow).
                composable(
                    Routes.SHOPPING,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(200)) },
                ) {
                    ShoppingScreen(
                        onOpenList = { id -> navController.navigate(Routes.shoppingDetail(id)) },
                        viewModel = shoppingVm,
                    )
                }

                // Feature/detail screens: inherit NavHost default (slide)
                composable(
                    Routes.SHOPPING_DETAIL,
                    arguments = listOf(navArgument("listId") { type = NavType.StringType }),
                ) { entry ->
                    ShoppingDetailScreen(
                        entry.arguments!!.getString("listId")!!,
                        onBack = { navController.popBackStack() },
                        viewModel = shoppingVm,
                    )
                }

                composable(Routes.MEAL) {
                    MealScreen(
                        onBack = { navController.popBackStack() },
                        onOpen = { id -> navController.navigate(Routes.mealDetail(id)) },
                        viewModel = mealVm,
                    )
                }
                composable(
                    Routes.MEAL_DETAIL,
                    arguments = listOf(navArgument("planId") { type = NavType.StringType }),
                ) { entry ->
                    MealDetailScreen(
                        entry.arguments!!.getString("planId")!!,
                        onBack = { navController.popBackStack() },
                        viewModel = mealVm,
                    )
                }

                composable(Routes.BIRTHDAY) {
                    BirthdayScreen(onBack = { navController.popBackStack() }, viewModel = birthdayVm)
                }

                composable(Routes.WISHLIST) {
                    WishlistScreen(
                        onBack = { navController.popBackStack() },
                        onOpen = { id -> navController.navigate(Routes.wishlistDetail(id)) },
                        viewModel = wishlistVm,
                    )
                }
                composable(
                    Routes.WISHLIST_DETAIL,
                    arguments = listOf(navArgument("wishlistId") { type = NavType.StringType }),
                ) { entry ->
                    WishlistDetailScreen(
                        entry.arguments!!.getString("wishlistId")!!,
                        onBack = { navController.popBackStack() },
                        viewModel = wishlistVm,
                    )
                }

                composable(
                    Routes.CHAT_DETAIL,
                    arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
                    deepLinks = listOf(navDeepLink { uriPattern = Routes.CHAT_DETAIL_DEEP_LINK }),
                ) { entry ->
                    ConversationScreen(
                        conversationId = entry.arguments!!.getString("conversationId")!!,
                        onBack = { navController.popBackStack() },
                        onNavigateTo = { id -> navController.navigate(Routes.chatDetail(id)) },
                        viewModel = chatVm,
                    )
                }

                // Family is reached from the Home dashboard (pushed, slide + back arrow).
                composable(Routes.FAMILY) {
                    FamilyScreen(onBack = { navController.popBackStack() }, viewModel = familyVm)
                }

                composable(Routes.PROFILE_EDIT) { ProfileEditScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.FAMILY_MAP) { FamilyMapScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}
