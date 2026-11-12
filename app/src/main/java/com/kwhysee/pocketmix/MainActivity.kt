package com.kwhysee.pocketmix

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kwhysee.pocketmix.ui.theme.PocketMixTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppScreen {
    LIBRARY,
    WORKSPACE,
}

private enum class DatabaseStatus {
    LOADING,
    READY,
    ERROR,
}

class MainActivity : ComponentActivity() {
    private lateinit var database: PocketMixDatabase

    private val saveHandler = Handler(Looper.getMainLooper())
    private var databaseStatus by mutableStateOf(DatabaseStatus.LOADING)
    private var currentScreen by mutableStateOf(AppScreen.LIBRARY)
    private var pockets by mutableStateOf(emptyList<PocketState>())
    private var playbackStates by mutableStateOf(
        emptyMap<String, PlaybackState>()
    )
    private var videos by mutableStateOf(emptyList<Video>())

    private val saveRunnable = Runnable {
        database.saveState(pockets, playbackStates)
    }

    private fun scheduleSave() {
        saveHandler.removeCallbacks(saveRunnable)
        saveHandler.postDelayed(saveRunnable, 500L)
    }

    private fun loadFromDatabase() {
        databaseStatus = DatabaseStatus.LOADING

        lifecycleScope.launch {
            val saved = try {
                withContext(Dispatchers.IO) {
                    database.loadWorkspace()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("PocketMixDatabase", "DB 불러오기 실패", error)
                databaseStatus = DatabaseStatus.ERROR
                return@launch
            }

            videos = saved.videos
            pockets = saved.pockets
            playbackStates = saved.playbackStates
            currentScreen = if (pockets.any {
                    it.status != PocketStatus.CLOSED
                }) {
                AppScreen.WORKSPACE
            } else {
                AppScreen.LIBRARY
            }
            databaseStatus = DatabaseStatus.READY
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = PocketMixDatabase(applicationContext)

        setContent {
            PocketMixTheme {
                var searchText by remember {
                    mutableStateOf("")
                }

                when (databaseStatus) {
                    DatabaseStatus.LOADING -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator()
                                Text("영상을 불러오는 중입니다.")
                            }
                        }
                    }

                    DatabaseStatus.ERROR -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "영상 보관함을 불러오지 못했습니다.",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Button(onClick = { loadFromDatabase() }) {
                                    Text("다시 시도")
                                }
                            }
                        }
                    }

                    DatabaseStatus.READY -> {
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
        }

        loadFromDatabase()
    }

    override fun onStop() {
        saveHandler.removeCallbacks(saveRunnable)
        if (databaseStatus == DatabaseStatus.READY) {
            database.saveState(pockets, playbackStates)
        }
        super.onStop()
    }

    override fun onDestroy() {
        saveHandler.removeCallbacks(saveRunnable)
        super.onDestroy()
        database.close()
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