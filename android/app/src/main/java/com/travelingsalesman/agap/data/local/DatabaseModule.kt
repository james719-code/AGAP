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
    entities = [
        PendingPacketEntity::class, 
        IncidentEntity::class,
        com.travelingsalesman.agap.data.local.entity.UserProfileEntity::class,
        com.travelingsalesman.agap.data.local.entity.MedicalInfoEntity::class,
        com.travelingsalesman.agap.data.local.entity.EmergencyContactEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AgapDatabase : RoomDatabase() {
    abstract fun pendingPacketDao(): PendingPacketDao
    abstract fun incidentDao(): IncidentDao
    abstract fun profileDao(): com.travelingsalesman.agap.data.local.dao.ProfileDao
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

    @Provides
    fun provideProfileDao(db: AgapDatabase): com.travelingsalesman.agap.data.local.dao.ProfileDao = db.profileDao()
}
