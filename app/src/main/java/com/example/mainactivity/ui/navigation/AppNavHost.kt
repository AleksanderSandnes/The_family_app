package com.example.mainactivity.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.example.mainactivity.ui.calendar.CalendarScreen
import com.example.mainactivity.ui.chat.ChatScreen
import com.example.mainactivity.ui.chat.ConversationScreen
import com.example.mainactivity.ui.family.FamilyScreen
import com.example.mainactivity.ui.home.HomeScreen
import com.example.mainactivity.ui.meal.MealDetailScreen
import com.example.mainactivity.ui.meal.MealScreen
import com.example.mainactivity.ui.profile.ProfileEditScreen
import com.example.mainactivity.ui.profile.ProfileScreen
import com.example.mainactivity.ui.settings.SettingsScreen
import com.example.mainactivity.ui.shopping.ShoppingDetailScreen
import com.example.mainactivity.ui.shopping.ShoppingScreen
import com.example.mainactivity.ui.wishlist.WishlistDetailScreen
import com.example.mainactivity.ui.wishlist.WishlistScreen

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
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomDestinations.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpen = { route -> navController.navigate(route) },
                    onOpenFamily = { navController.navigate(Routes.FAMILY) }
                )
            }
            composable(Routes.CALENDAR) { CalendarScreen() }
            composable(Routes.CHAT) { ChatScreen(onOpen = { id -> navController.navigate(Routes.chatDetail(id)) }) }
            composable(Routes.FAMILY) { FamilyScreen() }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onEdit = { navController.navigate(Routes.PROFILE_EDIT) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onSignedOut = { /* RootViewModel reacts */ }
                )
            }

            composable(Routes.SHOPPING) {
                ShoppingScreen(onBack = { navController.popBackStack() }, onOpenList = { id -> navController.navigate(Routes.shoppingDetail(id)) })
            }
            composable(
                Routes.SHOPPING_DETAIL,
                arguments = listOf(navArgument("listId") { type = NavType.LongType })
            ) { entry ->
                ShoppingDetailScreen(entry.arguments!!.getLong("listId"), onBack = { navController.popBackStack() })
            }

            composable(Routes.MEAL) {
                MealScreen(onBack = { navController.popBackStack() }, onOpen = { id -> navController.navigate(Routes.mealDetail(id)) })
            }
            composable(
                Routes.MEAL_DETAIL,
                arguments = listOf(navArgument("planId") { type = NavType.LongType })
            ) { entry ->
                MealDetailScreen(entry.arguments!!.getLong("planId"), onBack = { navController.popBackStack() })
            }

            composable(Routes.BIRTHDAY) { BirthdayScreen(onBack = { navController.popBackStack() }) }

            composable(Routes.WISHLIST) {
                WishlistScreen(onBack = { navController.popBackStack() }, onOpen = { id -> navController.navigate(Routes.wishlistDetail(id)) })
            }
            composable(
                Routes.WISHLIST_DETAIL,
                arguments = listOf(navArgument("wishlistId") { type = NavType.LongType })
            ) { entry ->
                WishlistDetailScreen(entry.arguments!!.getLong("wishlistId"), onBack = { navController.popBackStack() })
            }

            composable(
                Routes.CHAT_DETAIL,
                arguments = listOf(navArgument("conversationId") { type = NavType.LongType })
            ) { entry ->
                ConversationScreen(entry.arguments!!.getLong("conversationId"), onBack = { navController.popBackStack() })
            }

            composable(Routes.PROFILE_EDIT) { ProfileEditScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
