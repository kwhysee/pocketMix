package com.kwhysee.pocketmix

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    private var searchText by mutableStateOf("")
    private var isImportingVideo by mutableStateOf(false)
    private var importError by mutableStateOf<String?>(null)
    private var pockets by mutableStateOf(emptyList<PocketState>())
    private var playbackStates by mutableStateOf(
        emptyMap<String, PlaybackState>()
    )
    private var videos by mutableStateOf(emptyList<Video>())
    private var returningFromFloatingWindows = false

    private val requestOverlayPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startFloatingWindows()
        } else {
            importError = "떠 있는 창을 사용하려면 '다른 앱 위에 표시' 권한이 필요합니다."
        }
    }

    private val pickVideo = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importSelectedVideo(uri)
        }
    }

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

    private fun importSelectedVideo(uri: Uri) {
        if (isImportingVideo) return
        isImportingVideo = true

        lifecycleScope.launch {
            try {
                val video = withContext(Dispatchers.IO) {
                    importVideoFile(applicationContext, uri, database)
                }
                videos = (videos + video).sortedBy { it.videoId }
                searchText = ""
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("PocketMixImport", "영상 가져오기 실패", error)
                importError = "영상을 가져오지 못했습니다. MP4 파일을 확인하세요."
            } finally {
                isImportingVideo = false
            }
        }
    }

    private fun startFloatingWindows() {
        val videoIds = pockets
            .filter { it.status == PocketStatus.OPEN }
            .map { it.videoId }
            .filter { id -> videos.findUsableVideo(id) != null }

        if (videoIds.isEmpty()) {
            importError = "떠 있는 창으로 열 영상이 없습니다."
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission.launch(
                android.content.Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
            )
            return
        }

        // 플레이어를 멈추고 현재 위치를 직접 읽은 뒤, 작업 공간에서 해제한다.
        val currentPositions = ActivePocketPlayers.pauseAndReadPositions()
        playbackStates = playbackStates.toMutableMap().apply {
            currentPositions.forEach { (videoId, positionMs) ->
                this[videoId] = (this[videoId]
                    ?: PlaybackState(videoId = videoId)).copy(positionMs = positionMs)
            }
        }
        currentScreen = AppScreen.LIBRARY
        lifecycleScope.launch {
            val playersReleased = withTimeoutOrNull(1_500L) {
                while (ActivePocketPlayers.hasActivePlayers()) delay(16L)
                true
            } ?: false
            if (!playersReleased) {
                currentScreen = AppScreen.WORKSPACE
                importError = "기존 영상 플레이어를 정리하지 못했습니다. 다시 시도하세요."
                return@launch
            }
            try {
                saveHandler.removeCallbacks(saveRunnable)
                database.saveState(pockets, playbackStates)
                val intent = android.content.Intent(
                    this@MainActivity,
                    FloatingVideoService::class.java,
                ).apply {
                    action = FloatingVideoService.ACTION_START
                    putStringArrayListExtra(
                        FloatingVideoService.EXTRA_VIDEO_IDS,
                        ArrayList(videoIds),
                    )
                }
                returningFromFloatingWindows = true
                startForegroundService(intent)
                moveTaskToBack(true)
            } catch (error: Exception) {
                returningFromFloatingWindows = false
                currentScreen = AppScreen.WORKSPACE
                Log.e("PocketMixFloating", "떠 있는 창 시작 실패", error)
                importError = "떠 있는 창을 시작하지 못했습니다."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = PocketMixDatabase(applicationContext)

        setContent {
            PocketMixTheme {
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
                                    onImportVideo = {
                                        pickVideo.launch(arrayOf("video/mp4"))
                                    },
                                    isImportingVideo = isImportingVideo,
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
                                    onStartFloatingWindows = { startFloatingWindows() },
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

                if (importError != null) {
                    AlertDialog(
                        onDismissRequest = { importError = null },
                        title = { Text("작업을 완료하지 못했습니다") },
                        text = { Text(importError ?: "") },
                        confirmButton = {
                            TextButton(onClick = { importError = null }) {
                                Text("확인")
                            }
                        },
                    )
                }
            }
        }

        if (!FloatingVideoService.hasActiveSession()) {
            loadFromDatabase()
        }
    }

    override fun onStop() {
        saveHandler.removeCallbacks(saveRunnable)
        if (databaseStatus == DatabaseStatus.READY &&
            !returningFromFloatingWindows
        ) {
            database.saveState(pockets, playbackStates)
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::database.isInitialized &&
            (returningFromFloatingWindows || FloatingVideoService.hasActiveSession())
        ) {
            FloatingVideoService.finishActiveSession()
            returningFromFloatingWindows = false
            loadFromDatabase()
        }
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
            onImportVideo = {},
            isImportingVideo = false,
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
            onArrangePockets = {},
            onStartFloatingWindows = {},
        )
    }
}
