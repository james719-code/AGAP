package com.travelingsalesman.agap.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.travelingsalesman.agap.data.local.dao.IncidentDao
import com.travelingsalesman.agap.data.local.dao.PendingPacketDao
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [PendingPacketEntity::class, IncidentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AgapDatabase : RoomDatabase() {
    abstract fun pendingPacketDao(): PendingPacketDao
    abstract fun incidentDao(): IncidentDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAgapDatabase(@ApplicationContext context: Context): AgapDatabase {
        return Room.databaseBuilder(
            context,
            AgapDatabase::class.java,
            "agap_mobile.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun providePendingPacketDao(db: AgapDatabase): PendingPacketDao = db.pendingPacketDao()

    @Provides
    fun provideIncidentDao(db: AgapDatabase): IncidentDao = db.incidentDao()
}
