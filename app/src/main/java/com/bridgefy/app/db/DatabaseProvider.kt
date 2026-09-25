package com.bridgefy.app.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * Singleton provider for the SQLite database instance.
 * Must be initialized in Application.onCreate() before any DB access.
 */
object DatabaseProvider {

    lateinit var helper: AppDatabaseHelper
        private set

    lateinit var db: SQLiteDatabase
        private set

    fun init(context: Context) {
        helper = AppDatabaseHelper(context.applicationContext)
        db = helper.writableDatabase
    }
}
