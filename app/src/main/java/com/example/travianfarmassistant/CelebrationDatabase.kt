package com.example.travianfarmassistant

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Database khusus Celebration.
 *
 * Tidak mengubah database Resource Builder / Town Builder.
 */
class CelebrationDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "celebration.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE = "celebration"

        const val COL_ID = "id"
        const val COL_NEWDID = "newdid"
        const val COL_AUTO_PARTY = "isAutoParty"
        const val COL_VILLAGE = "village"
        const val COL_CULTURE_POINT = "culturePoint"
        const val COL_ONGOING = "ongoingCelebration"

        private const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS celebration (
                id TEXT PRIMARY KEY,
                newdid TEXT NOT NULL DEFAULT '',
                isAutoParty INTEGER NOT NULL DEFAULT 0,
                village TEXT NOT NULL DEFAULT '',
                culturePoint INTEGER NOT NULL DEFAULT 0,
                ongoingCelebration TEXT
            )
        """
    }

    data class Record(
        val id: String,
        val newdid: String,
        val isAutoParty: Boolean,
        val village: String,
        val culturePoint: Int,
        val ongoingCelebration: String?
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE $TABLE ADD COLUMN $COL_NEWDID TEXT NOT NULL DEFAULT ''"
            )
        }
    }

    fun upsert(
        id: String,
        newdid: String,
        village: String,
        culturePoint: Int,
        ongoingCelebration: String?,
        isAutoParty: Boolean? = null
    ) {
        if (id.isBlank() || newdid.isBlank()) return

        val values = ContentValues().apply {
            put(COL_ID, id)
            put(COL_NEWDID, newdid)
            put(COL_VILLAGE, village)
            put(COL_CULTURE_POINT, culturePoint)

            if (ongoingCelebration == null) {
                putNull(COL_ONGOING)
            } else {
                put(COL_ONGOING, ongoingCelebration)
            }

            if (isAutoParty != null) {
                put(COL_AUTO_PARTY, if (isAutoParty) 1 else 0)
            }
        }

        writableDatabase.insertWithOnConflict(
            TABLE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun setAutoParty(id: String, enabled: Boolean) {
        writableDatabase.update(
            TABLE,
            ContentValues().apply {
                put(COL_AUTO_PARTY, if (enabled) 1 else 0)
            },
            "$COL_ID = ?",
            arrayOf(id)
        )
    }

    fun get(id: String): Record? {
        readableDatabase.query(
            TABLE,
            null,
            "$COL_ID = ?",
            arrayOf(id),
            null,
            null,
            null
        ).use { c ->
            if (!c.moveToFirst()) return null

            return Record(
                id = c.getString(c.getColumnIndexOrThrow(COL_ID)),
                newdid = c.getString(c.getColumnIndexOrThrow(COL_NEWDID)),
                isAutoParty = c.getInt(c.getColumnIndexOrThrow(COL_AUTO_PARTY)) != 0,
                village = c.getString(c.getColumnIndexOrThrow(COL_VILLAGE)),
                culturePoint = c.getInt(c.getColumnIndexOrThrow(COL_CULTURE_POINT)),
                ongoingCelebration =
                    if (c.isNull(c.getColumnIndexOrThrow(COL_ONGOING))) {
                        null
                    } else {
                        c.getString(c.getColumnIndexOrThrow(COL_ONGOING))
                    }
            )
        }
    }

    fun getAll(): List<Record> {
        val result = mutableListOf<Record>()

        readableDatabase.query(
            TABLE,
            null,
            null,
            null,
            null,
            null,
            "$COL_VILLAGE COLLATE NOCASE ASC"
        ).use { c ->
            while (c.moveToNext()) {
                result += Record(
                    id = c.getString(c.getColumnIndexOrThrow(COL_ID)),
                    newdid = c.getString(c.getColumnIndexOrThrow(COL_NEWDID)),
                    isAutoParty = c.getInt(c.getColumnIndexOrThrow(COL_AUTO_PARTY)) != 0,
                    village = c.getString(c.getColumnIndexOrThrow(COL_VILLAGE)),
                    culturePoint = c.getInt(c.getColumnIndexOrThrow(COL_CULTURE_POINT)),
                    ongoingCelebration =
                        if (c.isNull(c.getColumnIndexOrThrow(COL_ONGOING))) {
                            null
                        } else {
                            c.getString(c.getColumnIndexOrThrow(COL_ONGOING))
                        }
                )
            }
        }

        return result
    }

    fun deleteAll() {
        writableDatabase.delete(TABLE, null, null)
    }
}
