package com.kwhysee.pocketmix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kwhysee.pocketmix.ui.theme.PocketMixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PocketMixTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PocketModelDemo(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun PocketModelDemo(modifier: Modifier = Modifier) {
    val video = Video(
        videoId = "sea-001",
        title = "오후의 해변",
        fileLocation = "sample/sea.mp4",
        durationMs = 380_000L,
    )

    val playback = PlaybackState(videoId = video.videoId)
        .seekTo(90_000L, video.durationMs)
        .withVolume(70)
        .normalized(video.durationMs)

    val pockets = emptyList<PocketState>()
        .openPocket(video.videoId)

    val summary = listOf(
        "pocketMix 모델 확인",
        "영상: ${video.title}",
        "시청 위치: ${playback.positionMs / 1_000}초",
        "음량: ${playback.volume}",
        "열린 Pocket: ${pockets.visiblePockets().size}개",
        "영상 정보: ${video.validationError() ?: "정상"}",
    ).joinToString("\n")

    Text(text = summary, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun PocketModelDemoPreview() {
    PocketMixTheme {
        PocketModelDemo()
    }
}