package com.example.mainactivity.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mainactivity.ui.auth.LoginScreen
import com.example.mainactivity.ui.auth.RegisterScreen
import com.example.mainactivity.ui.birthday.BirthdayScreen
import com.example.mainactivity.ui.birthday.BirthdayViewModel
import com.example.mainactivity.ui.calendar.CalendarScreen
import com.example.mainactivity.ui.calendar.CalendarViewModel
import com.example.mainactivity.ui.chat.ChatScreen
import com.example.mainactivity.ui.chat.ConversationScreen
import com.example.mainactivity.ui.family.FamilyScreen
import com.example.mainactivity.ui.home.HomeScreen
import com.example.mainactivity.ui.map.FamilyMapScreen
import com.example.mainactivity.ui.meal.MealDetailScreen
import com.example.mainactivity.ui.meal.MealScreen
import com.example.mainactivity.ui.meal.MealViewModel
import com.example.mainactivity.ui.profile.ProfileEditScreen
import com.example.mainactivity.ui.profile.ProfileScreen
import com.example.mainactivity.ui.settings.SettingsScreen
import com.example.mainactivity.ui.shopping.ShoppingDetailScreen
import com.example.mainactivity.ui.shopping.ShoppingScreen
import com.example.mainactivity.ui.shopping.ShoppingViewModel
import com.example.mainactivity.ui.wishlist.WishlistDetailScreen
import com.example.mainactivity.ui.wishlist.WishlistScreen
import com.example.mainactivity.ui.wishlist.WishlistViewModel

private data class BottomDest(val route: String, val label: String, val icon: ImageVector)

private val bottomDestinations = listOf(
    BottomDest(Routes.HOME, "Home", Icons.Filled.Home),
    BottomDest(Routes.CALENDAR, "Calendar", Icons.Filled.CalendarMonth),
    BottomDest(Routes.CHAT, "Chat", Icons.AutoMirrored.Filled.Chat),
    BottomDest(Routes.FAMILY, "Family", Icons.Filled.Groups),
    BottomDest(Routes.PROFILE, "Profile", Icons.Filled.Person)
)

@Composable
fun FamilyApp(rootViewModel: RootViewModel = viewModel()) {
    val gate by rootViewModel.gate.collectAsStateWithLifecycle()
    when (gate) {
        AuthGate.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        AuthGate.SignedOut -> AuthFlow()
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
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onAuthenticated = { },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun MainFlow() {
    // Hoist feature ViewModels to MainFlow scope (Activity-scoped).
    // init{} fires immediately on login — data loads in the background before user taps any screen.
    val shoppingVm: ShoppingViewModel = viewModel()
    val birthdayVm: BirthdayViewModel = viewModel()
    val wishlistVm: WishlistViewModel = viewModel()
    val mealVm: MealViewModel = viewModel()
    val calendarVm: CalendarViewModel = viewModel()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomDestinations.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
        ),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomDestinations.forEach { dest ->
                        val isHome = dest.route == Routes.HOME
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
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
            // Default: iOS-style horizontal slide for detail/feature screens
            enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
            exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(200)) },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300)) },
            popExitTransition = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)) }
        ) {
            // Bottom-tab destinations: crossfade only (no slide between sibling screens)
            composable(
                Routes.HOME,
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(200)) }
            ) {
                HomeScreen(
                    onOpen = { route -> navController.navigate(route) },
                    onOpenFamily = { navController.navigate(Routes.FAMILY) }
                )
            }
            composable(
                Routes.CALENDAR,
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(200)) }
            ) { CalendarScreen(viewModel = calendarVm) }
            composable(
                Routes.CHAT,
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(200)) }
            ) { ChatScreen(onOpen = { id -> navController.navigate(Routes.chatDetail(id)) }) }
            composable(
                Routes.FAMILY,
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(200)) }
            ) { FamilyScreen() }
            composable(
                Routes.PROFILE,
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(200)) }
            ) {
                ProfileScreen(
                    onEdit = { navController.navigate(Routes.PROFILE_EDIT) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onSignedOut = { /* RootViewModel reacts */ }
                )
            }

            // Feature/detail screens: inherit NavHost default (slide)
            composable(Routes.SHOPPING) {
                ShoppingScreen(
                    onBack = { navController.popBackStack() },
                    onOpenList = { id -> navController.navigate(Routes.shoppingDetail(id)) },
                    viewModel = shoppingVm
                )
            }
            composable(
                Routes.SHOPPING_DETAIL,
                arguments = listOf(navArgument("listId") { type = NavType.StringType })
            ) { entry ->
                ShoppingDetailScreen(
                    entry.arguments!!.getString("listId")!!,
                    onBack = { navController.popBackStack() },
                    viewModel = shoppingVm
                )
            }

            composable(Routes.MEAL) {
                MealScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { id -> navController.navigate(Routes.mealDetail(id)) },
                    viewModel = mealVm
                )
            }
            composable(
                Routes.MEAL_DETAIL,
                arguments = listOf(navArgument("planId") { type = NavType.StringType })
            ) { entry ->
                MealDetailScreen(
                    entry.arguments!!.getString("planId")!!,
                    onBack = { navController.popBackStack() },
                    viewModel = mealVm
                )
            }

            composable(Routes.BIRTHDAY) {
                BirthdayScreen(onBack = { navController.popBackStack() }, viewModel = birthdayVm)
            }

            composable(Routes.WISHLIST) {
                WishlistScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { id -> navController.navigate(Routes.wishlistDetail(id)) },
                    viewModel = wishlistVm
                )
            }
            composable(
                Routes.WISHLIST_DETAIL,
                arguments = listOf(navArgument("wishlistId") { type = NavType.StringType })
            ) { entry ->
                WishlistDetailScreen(
                    entry.arguments!!.getString("wishlistId")!!,
                    onBack = { navController.popBackStack() },
                    viewModel = wishlistVm
                )
            }

            composable(
                Routes.CHAT_DETAIL,
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
            ) { entry ->
                ConversationScreen(
                    conversationId = entry.arguments!!.getString("conversationId")!!,
                    onBack = { navController.popBackStack() },
                    onNavigateTo = { id -> navController.navigate(Routes.chatDetail(id)) }
                )
            }

            composable(Routes.PROFILE_EDIT) { ProfileEditScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.FAMILY_MAP) { FamilyMapScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
