package com.kwhysee.pocketmix

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class SavedWorkspace(
    val videos: List<Video>,
    val pockets: List<PocketState>,
    val playbackStates: Map<String, PlaybackState>,
)

class PocketMixDatabase(
    context: Context,
    databaseName: String = "pocketmix.db",
) : SQLiteOpenHelper(context, databaseName, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE videos (
                video_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                file_location TEXT NOT NULL,
                duration_ms INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE pocket_states (
                video_id TEXT PRIMARY KEY,
                x REAL NOT NULL,
                y REAL NOT NULL,
                width REAL NOT NULL,
                height REAL NOT NULL,
                window_order INTEGER NOT NULL,
                status TEXT NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE playback_states (
                video_id TEXT PRIMARY KEY,
                position_ms INTEGER NOT NULL,
                volume INTEGER NOT NULL,
                muted INTEGER NOT NULL
            )"""
        )

        SampleVideoCatalog.videos.forEach { video ->
            db.insertOrThrow("videos", null, ContentValues().apply {
                put("video_id", video.videoId)
                put("title", video.title)
                put("file_location", video.fileLocation)
                put("duration_ms", video.durationMs)
            })
        }
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        error("DB 버전 $oldVersion → $newVersion 변경에는 마이그레이션이 필요합니다.")
    }

    fun addVideo(video: Video) {
        video.validationError()?.let { error(it) }

        writableDatabase.insertOrThrow(
            "videos",
            null,
            ContentValues().apply {
                put("video_id", video.videoId)
                put("title", video.title)
                put("file_location", video.fileLocation)
                put("duration_ms", video.durationMs)
            },
        )
    }

    fun loadWorkspace(): SavedWorkspace {
        val db = readableDatabase

        val videos = mutableListOf<Video>()
        db.rawQuery(
            "SELECT video_id, title, file_location, duration_ms FROM videos ORDER BY video_id",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                videos += Video(
                    videoId = cursor.getString(0),
                    title = cursor.getString(1),
                    fileLocation = cursor.getString(2),
                    durationMs = cursor.getLong(3),
                )
            }
        }

        val pockets = mutableListOf<PocketState>()
        db.rawQuery(
            "SELECT video_id, x, y, width, height, window_order, status FROM pocket_states",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val status = runCatching {
                    PocketStatus.valueOf(cursor.getString(6))
                }.getOrDefault(PocketStatus.CLOSED)

                pockets += PocketState(
                    videoId = cursor.getString(0),
                    order = cursor.getInt(5),
                    status = status,
                ).resizeTo(
                    cursor.getFloat(3),
                    cursor.getFloat(4),
                ).moveTo(
                    cursor.getFloat(1),
                    cursor.getFloat(2),
                )
            }
        }

        val playbackStates = mutableMapOf<String, PlaybackState>()
        db.rawQuery(
            "SELECT video_id, position_ms, volume, muted FROM playback_states",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val videoId = cursor.getString(0)
                val video = videos.firstOrNull { it.videoId == videoId }
                val positionMs = cursor.getLong(1).coerceAtLeast(0L)

                playbackStates[videoId] = PlaybackState(
                    videoId = videoId,
                    positionMs = if (video == null) {
                        positionMs
                    } else {
                        positionMs.coerceAtMost(
                            video.durationMs.coerceAtLeast(0L)
                        )
                    },
                    volume = cursor.getInt(2).coerceIn(0, 100),
                    muted = cursor.getInt(3) != 0,
                )
            }
        }

        return SavedWorkspace(
            videos = videos,
            pockets = pockets,
            playbackStates = playbackStates,
        )
    }

    fun saveState(
        pockets: List<PocketState>,
        playbackStates: Map<String, PlaybackState>,
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("pocket_states", null, null)
            pockets.forEach { pocket ->
                db.insertOrThrow(
                    "pocket_states",
                    null,
                    ContentValues().apply {
                        put("video_id", pocket.videoId)
                        put("x", pocket.x)
                        put("y", pocket.y)
                        put("width", pocket.width)
                        put("height", pocket.height)
                        put("window_order", pocket.order)
                        put("status", pocket.status.name)
                    },
                )
            }

            db.delete("playback_states", null, null)
            playbackStates.values.forEach { state ->
                db.insertOrThrow(
                    "playback_states",
                    null,
                    ContentValues().apply {
                        put("video_id", state.videoId)
                        put("position_ms", state.positionMs)
                        put("volume", state.volume)
                        put("muted", if (state.muted) 1 else 0)
                    },
                )
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}