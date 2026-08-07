package com.travelingsalesman.agap.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.travelingsalesman.agap.data.local.dao.AgencyDao
import com.travelingsalesman.agap.data.local.dao.IncidentDao
import com.travelingsalesman.agap.data.local.dao.PendingPacketDao
import com.travelingsalesman.agap.data.local.dao.PredefinedMessageDao
import com.travelingsalesman.agap.data.local.dao.ResponderDao
import com.travelingsalesman.agap.data.local.entity.AgencyEntity
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.local.entity.PredefinedMessageEntity
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
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
        AgencyEntity::class,
        ResponderUnitEntity::class,
        PredefinedMessageEntity::class,
        com.travelingsalesman.agap.data.local.entity.UserProfileEntity::class,
        com.travelingsalesman.agap.data.local.entity.MedicalInfoEntity::class,
        com.travelingsalesman.agap.data.local.entity.EmergencyContactEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AgapDatabase : RoomDatabase() {
    abstract fun pendingPacketDao(): PendingPacketDao
    abstract fun incidentDao(): IncidentDao
    abstract fun profileDao(): com.travelingsalesman.agap.data.local.dao.ProfileDao
    abstract fun agencyDao(): AgencyDao
    abstract fun responderDao(): ResponderDao
    abstract fun predefinedMessageDao(): PredefinedMessageDao
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
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    @Provides
    fun providePendingPacketDao(db: AgapDatabase): PendingPacketDao = db.pendingPacketDao()

    @Provides
    fun provideIncidentDao(db: AgapDatabase): IncidentDao = db.incidentDao()

    @Provides
    fun provideProfileDao(db: AgapDatabase): com.travelingsalesman.agap.data.local.dao.ProfileDao = db.profileDao()

    @Provides
    fun provideAgencyDao(db: AgapDatabase): AgencyDao = db.agencyDao()

    @Provides
    fun provideResponderDao(db: AgapDatabase): ResponderDao = db.responderDao()

    @Provides
    fun providePredefinedMessageDao(db: AgapDatabase): PredefinedMessageDao = db.predefinedMessageDao()
}

