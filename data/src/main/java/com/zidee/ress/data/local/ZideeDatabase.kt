package com.zidee.ress.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.zidee.ress.data.local.dao.OptimizationHistoryDao
import com.zidee.ress.data.local.entity.OptimizationHistoryEntity

@Database(
    entities = [OptimizationHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ZideeDatabase : RoomDatabase() {
    abstract fun optimizationHistoryDao(): OptimizationHistoryDao

    companion object {
        private const val DATABASE_NAME = "zidee_ress.db"

        fun create(context: Context): ZideeDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ZideeDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
