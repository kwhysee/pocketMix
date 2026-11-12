package com.kwhysee.pocketmix

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as MediaPlayer
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.material3.Player
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class, ExperimentalApi::class)
@kotlin.OptIn(ExperimentalApi::class)
@Composable
fun SingleVideoPlayer(
    video: Video,
    playbackState: PlaybackState,
    onPlaybackStateChange: (PlaybackState) -> Unit,
    pauseAllRequest: Int,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        Text(text = "영상 미리보기: ${video.title}", modifier = modifier)
        return
    }

    val context = LocalContext.current
    val activity = context as ComponentActivity
    val latestPlaybackState = rememberUpdatedState(playbackState)
    val latestOnPlaybackStateChange =
        rememberUpdatedState(onPlaybackStateChange)

    val player = remember(context, video.videoId, video.fileLocation) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video.fileLocation))
            seekTo(playbackState.positionMs.coerceAtLeast(0L))
            volume = if (playbackState.muted) {
                0f
            } else {
                playbackState.volume.coerceIn(0, 100) / 100f
            }
            playWhenReady = false
            prepare()
        }
    }

    DisposableEffect(player, activity) {
        val savePosition = {
            val positionMs = player.currentPosition.coerceAtLeast(0L)
            val previousState = latestPlaybackState.value

            if (previousState.positionMs != positionMs) {
                latestOnPlaybackStateChange.value(
                    previousState.copy(positionMs = positionMs)
                )
            }
        }

        val listener = object : MediaPlayer.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(
                    "PocketMixPlayer",
                    "영상=${video.videoId}, 오류코드=${error.errorCode}, 원인=${error.cause?.message}",
                    error,
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    savePosition()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: MediaPlayer.PositionInfo,
                newPosition: MediaPlayer.PositionInfo,
                reason: Int,
            ) {
                savePosition()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == MediaPlayer.STATE_ENDED) {
                    savePosition()
                }
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                player.pause()
                savePosition()
            }
        }

        player.addListener(listener)
        activity.lifecycle.addObserver(observer)

        onDispose {
            savePosition()
            activity.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(5_000L)
            if (player.isPlaying) {
                val positionMs = player.currentPosition.coerceAtLeast(0L)
                val previousState = latestPlaybackState.value

                if (previousState.positionMs != positionMs) {
                    latestOnPlaybackStateChange.value(
                        previousState.copy(positionMs = positionMs)
                    )
                }
            }
        }
    }

    LaunchedEffect(player, playbackState.volume, playbackState.muted) {
        player.volume = if (playbackState.muted) {
            0f
        } else {
            playbackState.volume.coerceIn(0, 100) / 100f
        }
    }

    // 요청 번호가 바뀔 때만 실행된다. 이후 한 영상만 다시 재생할 수 있다.
    LaunchedEffect(player, pauseAllRequest) {
        if (pauseAllRequest > 0) {
            player.pause()
        }
    }

    Player(
        player = player,
        modifier = modifier,
        showControls = true,
    )
}