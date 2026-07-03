package com.nuvio.tv.core.di

import android.content.Context
import androidx.room.Room
import com.nuvio.tv.data.local.AppDatabase
import com.nuvio.tv.data.local.ConfigDao
import com.nuvio.tv.data.local.EpgDao
import com.nuvio.tv.data.local.FavoriteDao
import com.nuvio.tv.data.repository.IptvRepositoryImpl
import com.nuvio.tv.domain.repository.IptvRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object IptvModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "nuvio_iptv.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideConfigDao(db: AppDatabase): ConfigDao = db.configDao()

    @Provides
    @Singleton
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    @Singleton
    fun provideEpgDao(db: AppDatabase): EpgDao = db.epgDao()

    @Provides
    @Singleton
    fun provideIptvRepository(
        configDao: ConfigDao,
        favoriteDao: FavoriteDao,
        epgDao: EpgDao,
        okHttpClient: OkHttpClient
    ): IptvRepository = IptvRepositoryImpl(configDao, favoriteDao, epgDao, okHttpClient)
}
