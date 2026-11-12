package com.kwhysee.pocketmix

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private lateinit var database: PocketMixDatabase
    private val saveHandler = Handler(Looper.getMainLooper())
    private var pockets by mutableStateOf(emptyList<PocketState>())
    private var playbackStates by mutableStateOf(
        emptyMap<String, PlaybackState>()
    )
    private var videos: List<Video> = emptyList()

    private val saveRunnable = Runnable {
        database.saveState(pockets, playbackStates)
    }

    private fun scheduleSave() {
        saveHandler.removeCallbacks(saveRunnable)
        saveHandler.postDelayed(saveRunnable, 500L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = PocketMixDatabase(applicationContext)
        val saved = database.loadWorkspace()
        videos = saved.videos
        pockets = saved.pockets
        playbackStates = saved.playbackStates

        setContent {
            PocketMixTheme {
                var currentScreen by remember {
                    mutableStateOf(
                        if (pockets.any {
                                it.status != PocketStatus.CLOSED
                            }) {
                            AppScreen.WORKSPACE
                        } else {
                            AppScreen.LIBRARY
                        }
                    )
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
                            videos = videos,
                            pockets = pockets,
                            searchText = searchText,
                            onSearchTextChange = { newText ->
                                searchText = newText
                            },
                            onOpenPocket = { videoId ->
                                val nextOrder =
                                    (pockets.maxOfOrNull { it.order } ?: -1) + 1

                                pockets = if (pockets.any {
                                        it.videoId == videoId
                                    }) {
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
                                    val isFirstPocket = pockets.isEmpty()
                                    pockets + PocketState(
                                        videoId = videoId,
                                        x = if (isFirstPocket) 0.05f else 0.12f,
                                        y = if (isFirstPocket) 0.08f else 0.53f,
                                        order = nextOrder,
                                    )
                                }

                                scheduleSave()
                                currentScreen = AppScreen.WORKSPACE
                            },
                            modifier = Modifier.padding(innerPadding),
                        )

                        AppScreen.WORKSPACE -> WorkspaceScreen(
                            videos = videos,
                            pockets = pockets,
                            playbackStates = playbackStates,
                            onPlaybackStateChange = { updatedState ->
                                playbackStates = playbackStates +
                                        (updatedState.videoId to updatedState)
                                scheduleSave()
                            },
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
                                scheduleSave()
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
                                scheduleSave()
                            },
                            onArrangePockets = {
                                pockets = pockets.arrangeOpenPockets()
                                scheduleSave()
                            },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        saveHandler.removeCallbacks(saveRunnable)
        database.saveState(pockets, playbackStates)
        super.onStop()
    }

    override fun onDestroy() {
        saveHandler.removeCallbacks(saveRunnable)
        database.close()
        super.onDestroy()
    }
}

/**
 * 현재 열려 있는 Pocket만 정렬한다.
 * 최소화하거나 닫은 Pocket은 원래 상태와 좌표를 유지한다.
 */
private fun List<PocketState>.arrangeOpenPockets(): List<PocketState> {
    val openIds = this
        .filter { it.status == PocketStatus.OPEN }
        .sortedBy { it.order }
        .map { it.videoId }

    return map { pocket ->
        val index = openIds.indexOf(pocket.videoId)

        when {
            pocket.status != PocketStatus.OPEN || index < 0 -> pocket

            openIds.size == 1 -> pocket
                .resizeTo(newWidth = 0.90f, newHeight = 0.80f)
                .moveTo(newX = 0.05f, newY = 0.10f)

            else -> pocket
                .resizeTo(newWidth = 0.90f, newHeight = 0.46f)
                .moveTo(
                    newX = 0.05f,
                    newY = if (index == 0) 0.02f else 0.52f,
                )
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
            pockets = listOf(
                PocketState(videoId = "sample-001"),
                PocketState(
                    videoId = "sample-002",
                    x = 0.12f,
                    y = 0.53f,
                    order = 1,
                ),
            ),
            playbackStates = emptyMap(),
            onPlaybackStateChange = {},
            onBackToLibrary = {},
            onPocketChange = {},
            onPocketSelect = {},
            onArrangePockets = {},
        )
    }
}