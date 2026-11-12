package com.kwhysee.pocketmix

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kwhysee.pocketmix.ui.theme.PocketMixTheme

private enum class AppScreen {
    LIBRARY,
    WORKSPACE,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PocketMixTheme {
                var pockets by remember {
                    mutableStateOf(emptyList<PocketState>())
                }
                var currentScreen by remember {
                    mutableStateOf(AppScreen.LIBRARY)
                }
                var searchText by remember {
                    mutableStateOf("")
                }

                BackHandler(
                    enabled = currentScreen == AppScreen.WORKSPACE
                ) {
                    currentScreen = AppScreen.LIBRARY
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentScreen) {
                        AppScreen.LIBRARY -> LibraryScreen(
                            videos = SampleVideoCatalog.videos,
                            pockets = pockets,
                            searchText = searchText,
                            onSearchTextChange = { newText ->
                                searchText = newText
                            },
                            onOpenPocket = { videoId ->
                                val nextOrder =
                                    (pockets.maxOfOrNull { it.order } ?: -1) + 1

                                pockets = if (pockets.any { it.videoId == videoId }) {
                                    pockets.map { pocket ->
                                        if (pocket.videoId == videoId) {
                                            pocket.copy(
                                                status = PocketStatus.OPEN,
                                                order = nextOrder,
                                            )
                                        } else {
                                            pocket
                                        }
                                    }
                                } else {
                                    pockets + PocketState(
                                        videoId = videoId,
                                        order = nextOrder,
                                    )
                                }

                                currentScreen = AppScreen.WORKSPACE
                            },
                            modifier = Modifier.padding(innerPadding),
                        )

                        AppScreen.WORKSPACE -> WorkspaceScreen(
                            videos = SampleVideoCatalog.videos,
                            pockets = pockets,
                            onBackToLibrary = {
                                currentScreen = AppScreen.LIBRARY
                            },
                            onPocketChange = { updatedPocket ->
                                pockets = pockets.map { pocket ->
                                    if (pocket.videoId == updatedPocket.videoId) {
                                        updatedPocket
                                    } else {
                                        pocket
                                    }
                                }
                            },
                            onPocketSelect = { videoId ->
                                val nextOrder =
                                    (pockets.maxOfOrNull { it.order } ?: -1) + 1

                                pockets = pockets.map { pocket ->
                                    if (pocket.videoId == videoId) {
                                        pocket.copy(order = nextOrder)
                                    } else {
                                        pocket
                                    }
                                }
                            },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Library Light", showBackground = true)
@Preview(
    name = "Library Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun LibraryScreenPreview() {
    PocketMixTheme {
        LibraryScreen(
            videos = SampleVideoCatalog.videos,
            pockets = emptyList(),
            searchText = "",
            onSearchTextChange = {},
            onOpenPocket = {},
        )
    }
}

@Preview(name = "Workspace", showBackground = true)
@Composable
fun WorkspaceScreenPreview() {
    PocketMixTheme {
        WorkspaceScreen(
            videos = SampleVideoCatalog.videos,
            pockets = listOf(PocketState(videoId = "sample-001")),
            onBackToLibrary = {},
            onPocketChange = {},
            onPocketSelect = {},
        )
    }
}