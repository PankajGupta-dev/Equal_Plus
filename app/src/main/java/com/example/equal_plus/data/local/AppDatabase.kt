package com.example.equal_plus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.equal_plus.data.local.dao.ActionDao
import com.example.equal_plus.data.local.dao.CallDao
import com.example.equal_plus.data.local.dao.ConversationDao
import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.local.entity.ConversationEntity

@Database(
    entities = [
        CallEntity::class,
        ConversationEntity::class,
        ActionEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun callDao(): CallDao
    abstract fun conversationDao(): ConversationDao
    abstract fun actionDao(): ActionDao

    companion object {
        private const val DATABASE_NAME = "equal_plus_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
