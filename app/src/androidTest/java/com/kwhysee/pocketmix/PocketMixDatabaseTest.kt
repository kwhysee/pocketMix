package com.kwhysee.pocketmix

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PocketMixDatabaseTest {

    @Test
    fun videoAndIndependentStatesSurviveDatabaseReopen() {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext
        val testDatabaseName =
            "pocketmix_test_${UUID.randomUUID()}.db"

        val importedVideo = Video(
            videoId = "local-test-video",
            title = "테스트 영상",
            fileLocation = "file:///test-only.mp4",
            durationMs = 30_000L,
        )

        val pockets = listOf(
            PocketState(
                videoId = "sample-001",
                x = 0.05f,
                y = 0.08f,
                width = 0.85f,
                height = 0.38f,
                order = 0,
                status = PocketStatus.OPEN,
            ),
            PocketState(
                videoId = "sample-002",
                x = 0.12f,
                y = 0.52f,
                width = 0.75f,
                height = 0.40f,
                order = 1,
                status = PocketStatus.MINIMIZED,
            ),
            PocketState(
                videoId = importedVideo.videoId,
                order = 2,
                status = PocketStatus.CLOSED,
            ),
        )

        val playbackStates = mapOf(
            "sample-001" to PlaybackState(
                videoId = "sample-001",
                positionMs = 3_000L,
                volume = 25,
                muted = false,
            ),
            "sample-002" to PlaybackState(
                videoId = "sample-002",
                positionMs = 8_000L,
                volume = 70,
                muted = true,
            ),
            importedVideo.videoId to PlaybackState(
                videoId = importedVideo.videoId,
                positionMs = 1_000L,
                volume = 40,
                muted = false,
            ),
        )

        try {
            val firstDatabase =
                PocketMixDatabase(context, testDatabaseName)
            try {
                val initial = firstDatabase.loadWorkspace()
                assertTrue(initial.videos.any {
                    it.videoId == "sample-001"
                })
                assertTrue(initial.videos.any {
                    it.videoId == "sample-002"
                })

                firstDatabase.addVideo(importedVideo)
                firstDatabase.saveState(pockets, playbackStates)
            } finally {
                firstDatabase.close()
            }

            val reopenedDatabase =
                PocketMixDatabase(context, testDatabaseName)
            try {
                val restored = reopenedDatabase.loadWorkspace()
                val restoredPockets =
                    restored.pockets.associateBy { it.videoId }

                assertEquals(
                    importedVideo,
                    restored.videos.first {
                        it.videoId == importedVideo.videoId
                    },
                )
                assertEquals(pockets.size, restored.pockets.size)

                pockets.forEach { original ->
                    val actual = restoredPockets[original.videoId]
                    assertEquals(original.status, actual?.status)
                    assertEquals(original.order, actual?.order)
                    assertEquals(original.x, actual?.x ?: 0f, 0.001f)
                    assertEquals(original.y, actual?.y ?: 0f, 0.001f)
                    assertEquals(
                        original.width,
                        actual?.width ?: 0f,
                        0.001f,
                    )
                    assertEquals(
                        original.height,
                        actual?.height ?: 0f,
                        0.001f,
                    )
                }

                playbackStates.forEach { (videoId, original) ->
                    assertEquals(
                        original,
                        restored.playbackStates[videoId],
                    )
                }
            } finally {
                reopenedDatabase.close()
            }
        } finally {
            context.deleteDatabase(testDatabaseName)
        }
    }
}