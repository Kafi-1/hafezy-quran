package com.hafeziquran.app.di

import com.hafeziquran.app.data.repository.PrayerTimesRepositoryImpl
import com.hafeziquran.app.domain.repository.PrayerTimesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds domain interfaces to their data-layer implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    abstract fun bindPrayerTimesRepository(
        impl: PrayerTimesRepositoryImpl,
    ): PrayerTimesRepository
}