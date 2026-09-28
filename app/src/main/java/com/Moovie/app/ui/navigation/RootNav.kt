package com.Moovie.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.Moovie.app.ServiceLocator
import com.Moovie.app.ui.screens.auth.AuthScreen
import com.Moovie.app.ui.screens.settings.UpdateAvailableDialog
import com.Moovie.app.ui.screens.detail.DetailScreen
import com.Moovie.app.ui.screens.detail.DetailViewModel
import com.Moovie.app.ui.screens.discover.DiscoverScreen
import com.Moovie.app.ui.screens.downloads.DownloadsScreen
import com.Moovie.app.ui.screens.home.HomeScreen
import com.Moovie.app.ui.screens.notifications.NotificationsScreen
import com.Moovie.app.ui.screens.profile.ProfileScreen
import com.Moovie.app.ui.screens.search.SearchScreen
import com.Moovie.app.ui.screens.series.SeriesScreen
import com.Moovie.app.ui.screens.series.SeriesViewModel
import com.Moovie.app.ui.screens.recs.AiRecsScreen
import com.Moovie.app.ui.screens.trivia.TriviaScreen
import com.Moovie.app.ui.screens.watchlist.WatchlistScreen
import com.Moovie.app.ui.screens.party.PartyIntroScreen
import com.Moovie.app.ui.screens.party.PartyRoomScreen
import com.Moovie.app.ui.screens.player.PlayerScreen
import com.Moovie.app.ui.screens.player.PlayerViewModel
import com.Moovie.app.ui.screens.settings.SettingsScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Moovie.app.ui.screens.social.SocialScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val WATCHLIST = "watchlist"
    const val DISCOVER = "discover"
    const val PROFILE = "profile"
    const val SOCIAL = "social"
    const val SETTINGS = "settings"
    const val AI_RECS = "ai_recs"
    const val TRIVIA = "trivia"
    const val DOWNLOADS = "downloads"
    const val AUTH = "auth"
    const val PARTY_INTRO = "party_intro"
    const val PARTY_ROOM = "party_room/{code}"
    const val DETAIL = "detail/{mediaType}/{id}"
    const val NOTIFICATIONS = "notifications"
    const val SERIES = "series/{id}?s={s}&e={e}"
    const val PLAYER = "player/{mediaType}/{id}?s={s}&e={e}"

    fun detail(mediaType: String, id: Int) = "detail/$mediaType/$id"
    fun series(id: Int, season: Int = 1, episode: Int = 1) = "series/$id?s=$season&e=$episode"
    fun partyRoom(code: String) = "party_room/$code"
    fun player(mediaType: String, id: Int, season: Int = 1, episode: Int = 1) =
        "player/$mediaType/$id?s=$season&e=$episode"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.SEARCH, "Search", Icons.Filled.Search),
    Tab(Routes.WATCHLIST, "Watchlist", Icons.Filled.AddCircle),
    Tab(Routes.DISCOVER, "Discover", Icons.Filled.Theaters),
    Tab(Routes.PROFILE, "Profile", Icons.Filled.Person),
)

@Composable
fun RootNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBars = currentRoute in tabs.map { it.route }

    UpdateAvailableDialog()

    Scaffold(
        bottomBar = {
            if (showBars) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) { HomeScreen(nav, viewModel()) }
            composable(Routes.SEARCH) { SearchScreen(nav) }
            composable(Routes.WATCHLIST) { WatchlistScreen(nav) }
            composable(Routes.DISCOVER) { DiscoverScreen(nav) }
            composable(Routes.PROFILE) { ProfileScreen(nav) }
            composable(Routes.SOCIAL) { SocialScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(
                Routes.DETAIL,
                arguments = listOf(
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("id") { type = NavType.IntType },
                ),
            ) { entry ->
                val mediaType = entry.arguments?.getString("mediaType") ?: "movie"
                val id = entry.arguments?.getInt("id") ?: 0
                val vm: DetailViewModel = viewModel(
                    key = "detail-$mediaType-$id",
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                            DetailViewModel(mediaType, id) as T
                    },
                )
                DetailScreen(nav, vm)
            }
            composable(Routes.AI_RECS) { AiRecsScreen(nav) }
            composable(Routes.TRIVIA) { TriviaScreen(nav) }
            composable(Routes.DOWNLOADS) { DownloadsScreen(nav) }
            composable(Routes.AUTH) { AuthScreen(nav) }
            composable(Routes.PARTY_INTRO) { PartyIntroScreen(nav) }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(nav) }
            composable(
                Routes.PARTY_ROOM,
                arguments = listOf(navArgument("code") { type = NavType.StringType }),
            ) { entry ->
                PartyRoomScreen(nav, entry.arguments?.getString("code").orEmpty())
            }
            composable(
                Routes.PLAYER,
                arguments = listOf(
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("id") { type = NavType.IntType },
                    navArgument("s") { type = NavType.IntType; defaultValue = 1 },
                    navArgument("e") { type = NavType.IntType; defaultValue = 1 },
                ),
            ) { entry ->
                val mediaType = entry.arguments?.getString("mediaType") ?: "movie"
                val id = entry.arguments?.getInt("id") ?: 0
                val season = entry.arguments?.getInt("s") ?: 1
                val episode = entry.arguments?.getInt("e") ?: 1
                val vm: PlayerViewModel = viewModel(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                            PlayerViewModel(mediaType, id, season, episode) as T
                    }
                )
                PlayerScreen(nav, vm)
            }
            composable(
                Routes.SERIES,
                arguments = listOf(
                    navArgument("id") { type = NavType.IntType },
                    navArgument("s") { type = NavType.IntType; defaultValue = 1 },
                    navArgument("e") { type = NavType.IntType; defaultValue = 1 },
                ),
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: 0
                val season = entry.arguments?.getInt("s") ?: 1
                val episode = entry.arguments?.getInt("e") ?: 1
                val vm: SeriesViewModel = viewModel(
                    key = "series-$id",
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                            SeriesViewModel(id, season, episode) as T
                    },
                )
                SeriesScreen(nav, vm)
            }
        }
    }
}
