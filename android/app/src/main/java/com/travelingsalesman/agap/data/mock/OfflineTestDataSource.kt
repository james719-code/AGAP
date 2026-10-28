package com.travelingsalesman.agap.data.mock

import android.content.Context
import com.travelingsalesman.agap.data.local.AgapDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import javax.inject.Singleton

data class LocalTestAccount(val username: String, val role: String, val password: String)

/** The implementation and credentials exist only in the offlineDebug source set. */
interface OfflineTestDataSource {
    val accounts: List<LocalTestAccount>
    suspend fun prepareDatabase(database: AgapDatabase)
    fun respond(request: Request, token: String?): Response
}

object DisabledOfflineTestDataSource : OfflineTestDataSource {
    override val accounts = emptyList<LocalTestAccount>()
    override suspend fun prepareDatabase(database: AgapDatabase) = Unit
    override fun respond(request: Request, token: String?): Response = error("Offline test data is unavailable in this build")
}

@Module
@InstallIn(SingletonComponent::class)
object OfflineTestModule {
    @Provides
    @Singleton
    fun provideOfflineTestDataSource(@ApplicationContext context: Context, json: Json): OfflineTestDataSource =
        VariantOfflineTest.create(context, json)
}
