package com.kwhysee.pocketmix

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

fun importVideoFile(
    context: Context,
    sourceUri: Uri,
    database: PocketMixDatabase,
): Video {
    val videoId = "local-${UUID.randomUUID()}"
    val directory = File(context.filesDir, "videos")

    check(directory.exists() || directory.mkdirs()) {
        "영상 저장 폴더를 만들 수 없습니다."
    }

    val destination = File(directory, "$videoId.mp4")

    try {
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: error("선택한 파일을 열 수 없습니다.")

        input.use { source ->
            destination.outputStream().use { target ->
                source.copyTo(target)
            }
        }

        check(destination.length() > 0L) {
            "선택한 파일이 비어 있습니다."
        }

        val retriever = MediaMetadataRetriever()
        val durationMs = try {
            retriever.setDataSource(destination.absolutePath)
            retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }

        check(durationMs > 0L) {
            "영상 길이를 읽을 수 없습니다."
        }

        val displayName = context.contentResolver.query(
            sourceUri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "가져온 영상"

        val title = displayName
            .substringBeforeLast(".", displayName)
            .trim()
            .ifBlank { "가져온 영상" }

        val video = Video(
            videoId = videoId,
            title = title,
            fileLocation = Uri.fromFile(destination).toString(),
            durationMs = durationMs,
        )

        database.addVideo(video)
        return video
    } catch (error: Exception) {
        destination.delete()
        throw error
    }
}