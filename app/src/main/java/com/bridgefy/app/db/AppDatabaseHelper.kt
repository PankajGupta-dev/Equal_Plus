package com.bridgefy.app.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_VERSION = 6
        const val DATABASE_NAME = "bridgefy.db"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE messages (
                id TEXT PRIMARY KEY,
                senderId TEXT NOT NULL,
                targetId TEXT,
                type TEXT NOT NULL,
                payload TEXT NOT NULL,
                fileName TEXT,
                mimeType TEXT,
                timestamp INTEGER NOT NULL,
                ttl INTEGER NOT NULL,
                hopCount INTEGER NOT NULL,
                hopPath TEXT NOT NULL,
                status TEXT NOT NULL,
                ackForMessageId TEXT
            )
        """)

        db.execSQL("""
            CREATE INDEX index_messages_senderId ON messages(senderId)
        """)
        db.execSQL("""
            CREATE INDEX index_messages_targetId ON messages(targetId)
        """)
        db.execSQL("""
            CREATE INDEX index_messages_status ON messages(status)
        """)
        db.execSQL("""
            CREATE INDEX index_messages_timestamp ON messages(timestamp)
        """)

        db.execSQL("""
            CREATE TABLE seen_messages (
                id TEXT PRIMARY KEY,
                seenAt INTEGER NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE peers (
                deviceId TEXT PRIMARY KEY,
                displayName TEXT NOT NULL,
                lastSeen INTEGER NOT NULL,
                rssi INTEGER,
                transportType TEXT NOT NULL,
                discoveryType TEXT NOT NULL DEFAULT 'BLE',
                ipAddress TEXT,
                macAddress TEXT,
                isConnected INTEGER NOT NULL DEFAULT 0,
                bridgefyId TEXT,
                username TEXT,
                latitude REAL,
                longitude REAL,
                location_accuracy_meters REAL,
                location_updated_at INTEGER
            )
        """)
        
        db.execSQL("""
            CREATE INDEX index_peers_lastSeen ON peers(lastSeen)
        """)

        db.execSQL("""
            CREATE TABLE call_logs (
                id TEXT PRIMARY KEY,
                callerId TEXT NOT NULL,
                receiverId TEXT NOT NULL,
                startTime INTEGER NOT NULL,
                endTime INTEGER,
                duration INTEGER DEFAULT 0,
                status TEXT NOT NULL,
                callType TEXT NOT NULL DEFAULT 'VOICE'
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            // Peers are transient (5-min staleness), safe to recreate
            db.execSQL("DROP TABLE IF EXISTS messages")
            db.execSQL("DROP TABLE IF EXISTS seen_messages")
            db.execSQL("DROP TABLE IF EXISTS peers")
            db.execSQL("DROP TABLE IF EXISTS call_logs")
            onCreate(db)
            return
        }
        if (oldVersion < 4) {
            // Add location columns to peers — non-destructive ALTER TABLE
            db.execSQL("ALTER TABLE peers ADD COLUMN latitude REAL")
            db.execSQL("ALTER TABLE peers ADD COLUMN longitude REAL")
            db.execSQL("ALTER TABLE peers ADD COLUMN location_accuracy_meters REAL")
            db.execSQL("ALTER TABLE peers ADD COLUMN location_updated_at INTEGER")
        }
        if (oldVersion < 5) {
            // Add call_logs table — non-destructive
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS call_logs (
                    id TEXT PRIMARY KEY,
                    callerId TEXT NOT NULL,
                    receiverId TEXT NOT NULL,
                    startTime INTEGER NOT NULL,
                    endTime INTEGER,
                    duration INTEGER DEFAULT 0,
                    status TEXT NOT NULL
                )
            """)
        }
        if (oldVersion < 6) {
            // Add callType column — non-destructive
            try {
                db.execSQL("ALTER TABLE call_logs ADD COLUMN callType TEXT NOT NULL DEFAULT 'VOICE'")
            } catch (e: Exception) {
                // Already exists or other error
            }
        }
    }
}
