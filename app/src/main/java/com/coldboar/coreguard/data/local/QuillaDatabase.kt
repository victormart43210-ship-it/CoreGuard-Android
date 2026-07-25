package com.coldboar.coreguard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.coldboar.coreguard.data.local.dao.QuillaLearningDao
import com.coldboar.coreguard.data.local.entity.QuillaHypothesisEntity

@Database(entities = [QuillaHypothesisEntity::class], version = 1, exportSchema = false)
abstract class QuillaDatabase : RoomDatabase() {

    abstract fun quillaLearningDao(): QuillaLearningDao

    companion object {
        @Volatile private var INSTANCE: QuillaDatabase? = null

        fun getInstance(context: Context): QuillaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    QuillaDatabase::class.java,
                    "quilla_database"
                ).build().also { INSTANCE = it }
            }
    }
}
