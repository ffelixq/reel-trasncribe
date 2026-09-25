package com.ffelixq.reeltranscribe

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class TranscriptRecord(
    val id: Long = 0,
    val sourceUrl: String,
    val platform: String,
    val transcript: String,
    val language: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

class TranscriptDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    "reel_transcribe.db",
    null,
    1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE transcripts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_url TEXT NOT NULL,
                platform TEXT NOT NULL,
                transcript TEXT NOT NULL,
                language TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(record: TranscriptRecord): Long {
        val values = ContentValues().apply {
            put("source_url", record.sourceUrl)
            put("platform", record.platform)
            put("transcript", record.transcript)
            put("language", record.language)
            put("created_at", record.createdAt)
        }
        return writableDatabase.insertOrThrow("transcripts", null, values)
    }

    fun listAll(limit: Int = 200): List<TranscriptRecord> {
        val items = mutableListOf<TranscriptRecord>()
        readableDatabase.query(
            "transcripts",
            arrayOf("id", "source_url", "platform", "transcript", "language", "created_at"),
            null,
            null,
            null,
            null,
            "created_at DESC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items += TranscriptRecord(
                    id = cursor.getLong(0),
                    sourceUrl = cursor.getString(1),
                    platform = cursor.getString(2),
                    transcript = cursor.getString(3),
                    language = if (cursor.isNull(4)) null else cursor.getString(4),
                    createdAt = cursor.getLong(5)
                )
            }
        }
        return items
    }

    fun delete(id: Long) {
        writableDatabase.delete("transcripts", "id = ?", arrayOf(id.toString()))
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var backendUrl: String
        get() = prefs.getString("backend_url", "") ?: ""
        set(value) = prefs.edit().putString("backend_url", value.trim()).apply()

    var accessToken: String
        get() = prefs.getString("access_token", "") ?: ""
        set(value) = prefs.edit().putString("access_token", value.trim()).apply()
}
