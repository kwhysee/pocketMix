package com.kwhysee.pocketmix

fun List<PocketState>.openPocket(videoId: String): List<PocketState> {
    val nextOrder = (maxOfOrNull { it.order } ?: 0) + 1
    val alreadyExists = any { it.videoId == videoId }

    return if (alreadyExists) {
        map { pocket ->
            if (pocket.videoId == videoId) {
                pocket.copy(status = PocketStatus.OPEN, order = nextOrder)
            } else {
                pocket
            }
        }
    } else {
        this + PocketState(videoId = videoId, order = nextOrder)
    }
}

fun List<PocketState>.setPocketStatus(
    videoId: String,
    status: PocketStatus,
): List<PocketState> {
    return map { pocket ->
        if (pocket.videoId == videoId) pocket.copy(status = status)
        else pocket
    }
}

fun List<PocketState>.visiblePockets(): List<PocketState> {
    return filter { it.status == PocketStatus.OPEN }
        .sortedBy { it.order }
}