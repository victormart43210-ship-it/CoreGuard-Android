package com.coldboar.coreguard.di

import android.content.Context
import androidx.room.Room
import com.coldboar.coreguard.data.local.QuillaDatabase
import com.coldboar.coreguard.data.local.dao.QuillaLearningDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object QuillaModule {

    @Provides
    @Singleton
    fun provideQuillaDatabase(@ApplicationContext context: Context): QuillaDatabase =
        Room.databaseBuilder(context, QuillaDatabase::class.java, "quilla_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideQuillaLearningDao(db: QuillaDatabase): QuillaLearningDao =
        db.quillaLearningDao()
}
