package com.anisync.android.presentation.mal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.anisync.android.presentation.navigation.Discover
import com.anisync.android.presentation.navigation.Library
import com.anisync.android.presentation.navigation.MalNativeDetails
import com.anisync.android.presentation.navigation.Profile

/**
 * MAL root graph hosted by Kizomi's shared compact/rail scaffold.
 *
 * It deliberately registers only provider-supported roots. AniList-only Feed, Forum, profile-data,
 * calendar and social destinations are absent rather than hidden behind a provider fallback.
 */
@Composable
fun MalSharedNavHost(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<Library> {
            MalSharedLibraryScreen(
                onMediaClick = { key ->
                    navController.navigate(MalNativeDetails(key.mediaType.name, key.malId))
                },
            )
        }
        composable<Discover> {
            MalCatalogScreen(
                onBackClick = {},
                onMediaClick = { key ->
                    navController.navigate(MalNativeDetails(key.mediaType.name, key.malId))
                },
                showBackButton = false,
            )
        }
        composable<Profile> {
            MalSharedAccountScreen()
        }
        composable<MalNativeDetails> {
            MalDetailsScreen(
                onBackClick = { navController.popBackStack() },
                onRelatedClick = { key ->
                    navController.navigate(MalNativeDetails(key.mediaType.name, key.malId))
                },
            )
        }
    }
}
