package com.travelingsalesman.agap.data.repository

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTelemetryRepository(
        impl: TelemetryRepositoryImpl
    ): TelemetryRepository

    @Binds
    @Singleton
    abstract fun bindIncidentRepository(
        impl: IncidentRepositoryImpl
    ): IncidentRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindResponderRepository(
        impl: ResponderRepositoryImpl
    ): ResponderRepository

    @Binds
    @Singleton
    abstract fun bindAgencyRepository(
        impl: AgencyRepositoryImpl
    ): AgencyRepository

    @Binds
    @Singleton
    abstract fun bindPredefinedMessageRepository(
        impl: PredefinedMessageRepositoryImpl
    ): PredefinedMessageRepository

    @Binds
    @Singleton
    abstract fun bindEmergencyTypeRepository(
        impl: EmergencyTypeRepositoryImpl
    ): EmergencyTypeRepository
}
