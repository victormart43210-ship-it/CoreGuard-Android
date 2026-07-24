package com.coldboar.coreguard.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.coldboar.coreguard.data.local.dao.QuillaLearningDao
import com.coldboar.coreguard.data.local.entity.QuillaDeviceProfileEntity
import com.coldboar.coreguard.data.local.entity.QuillaHypothesisEntity
import com.coldboar.coreguard.data.local.entity.QuillaLearningJournalEntity

@Database(
    entities = [
        QuillaHypothesisEntity::class,
        QuillaDeviceProfileEntity::class,
        QuillaLearningJournalEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class QuillaDatabase : RoomDatabase() {
    abstract fun quillaLearningDao(): QuillaLearningDao
}
