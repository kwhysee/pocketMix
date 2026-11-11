package com.kwhysee.pocketmix

fun Video.validationError(): String? {
    if (videoId.isBlank()) return "영상 ID가 비어 있습니다."
    if (title.isBlank()) return "영상 제목이 비어 있습니다."
    if (fileLocation.isBlank()) return "영상 파일 위치가 비어 있습니다."
    if (durationMs <= 0L) return "영상 길이가 올바르지 않습니다."
    return null
}

fun PlaybackState.normalized(durationMs: Long): PlaybackState {
    val safeDuration = durationMs.coerceAtLeast(0L)
    return copy(
        positionMs = positionMs.coerceIn(0L, safeDuration),
        volume = volume.coerceIn(0, 100),
    )
}

fun PocketState.normalized(): PocketState {
    val safeWidth = width.takeIf { it.isFinite() }
        ?.coerceIn(0.25f, 1f) ?: 0.85f
    val safeHeight = height.takeIf { it.isFinite() }
        ?.coerceIn(0.25f, 1f) ?: 0.38f
    val safeX = x.takeIf { it.isFinite() }
        ?.coerceIn(0f, 1f - safeWidth) ?: 0f
    val safeY = y.takeIf { it.isFinite() }
        ?.coerceIn(0f, 1f - safeHeight) ?: 0f

    return copy(
        x = safeX,
        y = safeY,
        width = safeWidth,
        height = safeHeight,
        order = order.coerceAtLeast(0),
    )
}

fun List<Video>.findUsableVideo(videoId: String): Video? {
    val video = firstOrNull { it.videoId == videoId } ?: return null
    return if (video.validationError() == null) video else null
}