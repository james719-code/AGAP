package com.travelingsalesman.agap.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.travelingsalesman.agap.data.local.dao.AdminAlertDao
import com.travelingsalesman.agap.data.local.dao.AgencyDao
import com.travelingsalesman.agap.data.local.dao.CatalogDao
import com.travelingsalesman.agap.data.local.dao.EmergencyTypeDao
import com.travelingsalesman.agap.data.local.dao.IncidentDao
import com.travelingsalesman.agap.data.local.dao.MessageDao
import com.travelingsalesman.agap.data.local.dao.MeshDeliveryEventDao
import com.travelingsalesman.agap.data.local.dao.NotificationDao
import com.travelingsalesman.agap.data.local.dao.PendingPacketDao
import com.travelingsalesman.agap.data.local.dao.PredefinedMessageDao
import com.travelingsalesman.agap.data.local.dao.RelayDao
import com.travelingsalesman.agap.data.local.dao.ReportDao
import com.travelingsalesman.agap.data.local.dao.ResponderDao
import com.travelingsalesman.agap.data.local.entity.AdminAlertEntity
import com.travelingsalesman.agap.data.local.entity.AgencyEntity
import com.travelingsalesman.agap.data.local.entity.CachedAgencyServiceAreaEntity
import com.travelingsalesman.agap.data.local.entity.CachedAgencyTypeEntity
import com.travelingsalesman.agap.data.local.entity.CachedBarangayEntity
import com.travelingsalesman.agap.data.local.entity.CachedCatalogVersionEntity
import com.travelingsalesman.agap.data.local.entity.CachedEmergencySubtypeEntity
import com.travelingsalesman.agap.data.local.entity.EmergencyTypeEntity
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.IncidentMessageEntity
import com.travelingsalesman.agap.data.local.entity.LocalIncidentReportEntity
import com.travelingsalesman.agap.data.local.entity.MeshDeliveryEventEntity
import com.travelingsalesman.agap.data.local.entity.NotificationEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.local.entity.PredefinedMessageEntity
import com.travelingsalesman.agap.data.local.entity.RelayQueueEntity
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import com.travelingsalesman.agap.data.local.entity.SeenMeshPacketEntity
import com.travelingsalesman.agap.data.local.migration.MIGRATION_8_9
import com.travelingsalesman.agap.data.local.migration.MIGRATION_9_10
import com.travelingsalesman.agap.data.local.migration.MIGRATION_10_11
import com.travelingsalesman.agap.data.local.migration.MIGRATION_11_12
import com.travelingsalesman.agap.data.local.migration.MIGRATION_12_13
import com.travelingsalesman.agap.data.local.migration.MIGRATION_13_14
import com.travelingsalesman.agap.data.local.migration.MIGRATION_14_15
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.travelingsalesman.agap.data.local.dao.PendingSyncDao
import com.travelingsalesman.agap.data.local.entity.PendingSyncEntity

@Database(
    entities = [
        PendingPacketEntity::class,
        MeshDeliveryEventEntity::class,
        IncidentEntity::class,
        AgencyEntity::class,
        ResponderUnitEntity::class,
        PredefinedMessageEntity::class,
        EmergencyTypeEntity::class,
        com.travelingsalesman.agap.data.local.entity.UserProfileEntity::class,
        com.travelingsalesman.agap.data.local.entity.MedicalInfoEntity::class,
        com.travelingsalesman.agap.data.local.entity.EmergencyContactEntity::class,
        LocalIncidentReportEntity::class,
        SeenMeshPacketEntity::class,
        RelayQueueEntity::class,
        CachedCatalogVersionEntity::class,
        CachedEmergencySubtypeEntity::class,
        CachedAgencyTypeEntity::class,
        CachedAgencyServiceAreaEntity::class,
        CachedBarangayEntity::class,
        PendingSyncEntity::class,
        IncidentMessageEntity::class,
        NotificationEntity::class,
        AdminAlertEntity::class
    ],
    version = 16,
    exportSchema = true
)
abstract class AgapDatabase : RoomDatabase() {
    abstract fun pendingPacketDao(): PendingPacketDao
    abstract fun meshDeliveryEventDao(): MeshDeliveryEventDao
    abstract fun incidentDao(): IncidentDao
    abstract fun profileDao(): com.travelingsalesman.agap.data.local.dao.ProfileDao
    abstract fun agencyDao(): AgencyDao
    abstract fun responderDao(): ResponderDao
    abstract fun predefinedMessageDao(): PredefinedMessageDao
    abstract fun emergencyTypeDao(): EmergencyTypeDao
    abstract fun reportDao(): ReportDao
    abstract fun relayDao(): RelayDao
    abstract fun catalogDao(): CatalogDao
    abstract fun pendingSyncDao(): PendingSyncDao
    abstract fun messageDao(): MessageDao
    abstract fun notificationDao(): NotificationDao
    abstract fun adminAlertDao(): AdminAlertDao
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
            backendStorageName("agap_mobile") + ".db"
        )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                runCatching {
                    db.query("PRAGMA synchronous = NORMAL;").use { it.moveToFirst() }
                    db.query("PRAGMA temp_store = MEMORY;").use { it.moveToFirst() }
                    db.query("PRAGMA busy_timeout = 5000;").use { it.moveToFirst() }
                    db.query("PRAGMA cache_size = -16000;").use { it.moveToFirst() }
                    db.query("PRAGMA wal_autocheckpoint = 1000;").use { it.moveToFirst() }
                }
            }
        })
        .addMigrations(
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15
        )
        // Development databases older than the first supported migration can
        // still recover instead of crashing; normal v8+ upgrades are preserved.
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun providePendingPacketDao(db: AgapDatabase): PendingPacketDao = db.pendingPacketDao()

    @Provides
    fun provideMeshDeliveryEventDao(db: AgapDatabase): MeshDeliveryEventDao = db.meshDeliveryEventDao()

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

    @Provides
    fun provideEmergencyTypeDao(db: AgapDatabase): EmergencyTypeDao = db.emergencyTypeDao()

    @Provides
    fun provideReportDao(db: AgapDatabase): ReportDao = db.reportDao()

    @Provides
    fun provideRelayDao(db: AgapDatabase): RelayDao = db.relayDao()

    @Provides
    fun provideCatalogDao(db: AgapDatabase): CatalogDao = db.catalogDao()

    @Provides
    fun providePendingSyncDao(db: AgapDatabase): PendingSyncDao = db.pendingSyncDao()

    @Provides
    fun provideMessageDao(db: AgapDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideNotificationDao(db: AgapDatabase): NotificationDao = db.notificationDao()

    @Provides
    fun provideAdminAlertDao(db: AgapDatabase): AdminAlertDao = db.adminAlertDao()
}
