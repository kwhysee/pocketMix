package com.kwhysee.pocketmix

fun PlaybackState.seekTo(
    requestedMs: Long,
    durationMs: Long,
): PlaybackState {
    val endMs = durationMs.coerceAtLeast(0L)
    return copy(positionMs = requestedMs.coerceIn(0L, endMs))
}

fun PlaybackState.withVolume(requestedVolume: Int): PlaybackState {
    return copy(
        volume = requestedVolume.coerceIn(0, 100),
        muted = false,
    )
}

fun PlaybackState.toggleMute(): PlaybackState {
    return copy(muted = !muted)
}

fun PocketState.moveTo(newX: Float, newY: Float): PocketState {
    return copy(
        x = newX.coerceIn(0f, (1f - width).coerceAtLeast(0f)),
        y = newY.coerceIn(0f, (1f - height).coerceAtLeast(0f)),
    )
}

fun PocketState.resizeTo(newWidth: Float, newHeight: Float): PocketState {
    val safeWidth = newWidth.coerceIn(0.25f, 1f)
    val safeHeight = newHeight.coerceIn(0.25f, 1f)

    return copy(
        width = safeWidth,
        height = safeHeight,
        x = x.coerceIn(0f, 1f - safeWidth),
        y = y.coerceIn(0f, 1f - safeHeight),
    )
}