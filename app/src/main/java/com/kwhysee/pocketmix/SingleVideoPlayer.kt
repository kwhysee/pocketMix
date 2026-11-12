package com.kwhysee.pocketmix

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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

@OptIn(UnstableApi::class, ExperimentalApi::class)
@kotlin.OptIn(ExperimentalApi::class)
@Composable
fun SingleVideoPlayer(
    video: Video,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        Text(text = "영상 미리보기: ${video.title}", modifier = modifier)
        return
    }

    val context = LocalContext.current
    val activity = context as ComponentActivity

    val player = remember(context, video.videoId, video.fileLocation) {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : MediaPlayer.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(
                        "PocketMixPlayer",
                        "영상=${video.videoId}, 오류코드=${error.errorCode}, 원인=${error.cause?.message}",
                        error,
                    )
                }
            })

            setMediaItem(MediaItem.fromUri(video.fileLocation))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                player.pause()
            }
        }

        activity.lifecycle.addObserver(observer)

        onDispose {
            activity.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    Player(
        player = player,
        modifier = modifier,
        showControls = true,
    )
}