package com.travelingsalesman.agap.core.network

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.travelingsalesman.agap.BuildConfig
import com.travelingsalesman.agap.data.mock.OfflineTestDataSource
import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.remote.AgencyApiService
import com.travelingsalesman.agap.data.remote.AuthApiService
import com.travelingsalesman.agap.data.remote.IncidentApiService
import com.travelingsalesman.agap.data.remote.MessageApiService
import com.travelingsalesman.agap.data.remote.NotificationApiService
import com.travelingsalesman.agap.data.remote.ResponderApiService
import com.travelingsalesman.agap.data.remote.RoutingGraphApiService
import com.travelingsalesman.agap.data.remote.dto.RefreshRequestDto
import com.travelingsalesman.agap.data.remote.dto.RefreshResponseDto
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Raised before an HTTP/WebSocket connection is opened when AGAP is in BLE-only mode.
 */
class BleOnlyModeNetworkException : IOException(
    "Internet transport is disabled while BLE mesh only mode is selected."
)

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val NULL_BODY_PEEK_BYTES = 16L
    private val hostedRefreshLock = Any()
    private var lastHostedRotation: Pair<String, String>? = null

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        tokenManager: AuthTokenManager,
        serverConfigManager: ServerConfigManager,
        offlineTestDataSource: OfflineTestDataSource,
        json: Json
    ): OkHttpClient {
        val cache = Cache(File(context.cacheDir, "http_cache"), 10L * 1024 * 1024)
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG && !BuildConfig.HOSTED_BACKEND) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        // This is intentionally the first application interceptor. It prevents
        // Retrofit and WebSocket calls from opening a socket in BLE-only mode,
        // even if Wi-Fi or mobile data is currently available.
        val transportPolicyInterceptor = Interceptor { chain ->
            val mode = if (serverConfigManager.isTransmissionModeLoaded) {
                serverConfigManager.currentTransmissionMode
            } else {
                // DataStore is asynchronous. Block only the first request after a
                // cold process start; all later requests use the volatile mirror.
                runBlocking { serverConfigManager.getTransmissionMode() }
            }

            if (mode == TransmissionMode.FORCE_BLE_MESH) {
                val request = chain.request()
                BleMeshLog.w(
                    "NETWORK_BLOCKED_BLE_ONLY",
                    "method" to request.method,
                    "host" to request.url.host
                )
                throw BleOnlyModeNetworkException()
            }
            chain.proceed(chain.request())
        }

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val authEndpoint = original.url.encodedPath.substringAfterLast("/api/v1/").startsWith("auth/")
            val token = if (tokenManager.isAuthTokenLoaded) {
                tokenManager.currentAuthToken
            } else {
                // Same pattern as transport/server settings: one blocking warm-up
                // at most, not one DataStore read per network request.
                runBlocking { tokenManager.getAuthToken() }
            }
            val requestBuilder = original.newBuilder()
            if (!token.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            val response = chain.proceed(requestBuilder.build())
            if (response.code != 401 || token.isNullOrEmpty() || authEndpoint) return@Interceptor response
            if (!BuildConfig.HOSTED_BACKEND) {
                runBlocking { tokenManager.clearAuthSession(token) }
                return@Interceptor response
            }

            val error = response.newBuilder().body(response.peekBody(16_384L)).build()
            response.close()
            val nextToken = synchronized(hostedRefreshLock) {
                val current = runBlocking { tokenManager.getAuthToken() }
                if (current != token) {
                    return@synchronized current?.takeIf { lastHostedRotation == (token to it) }
                }
                val refresh = runBlocking { tokenManager.getRefreshToken() }
                if (refresh.isNullOrBlank()) {
                    runBlocking { tokenManager.clearAuthSession(token) }
                    return@synchronized null
                }
                val refreshRequest = Request.Builder()
                    .url("${BuildConfig.BASE_URL}/auth/refresh")
                    .post(json.encodeToString(RefreshRequestDto(refresh)).toRequestBody("application/json".toMediaType()))
                    .build()
                chain.proceed(refreshRequest).use { refreshed ->
                    when {
                        refreshed.isSuccessful -> {
                            val body = refreshed.body?.string() ?: throw IOException("Missing session refresh response")
                            val tokens = try {
                                json.decodeFromString<RefreshResponseDto>(body)
                            } catch (e: kotlinx.serialization.SerializationException) {
                                throw IOException("Invalid session refresh response", e)
                            }
                            runBlocking { tokenManager.replaceSessionTokens(token, tokens.token, tokens.refreshToken) }
                                ?.also { lastHostedRotation = token to it }
                        }
                        refreshed.code == 401 || refreshed.code == 403 -> {
                            runBlocking { tokenManager.clearAuthSession(token) }
                            null
                        }
                        else -> throw IOException("Session refresh unavailable (HTTP ${refreshed.code})")
                    }
                }
            }
            if (nextToken.isNullOrBlank()) return@Interceptor error
            error.close()
            chain.proceed(original.newBuilder().header("Authorization", "Bearer $nextToken").build()).also {
                if (it.code == 401) runBlocking { tokenManager.clearAuthSession(nextToken) }
            }
        }

        val nullResponseSanitizerInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body
            if (response.isSuccessful && body != null) {
                // Previous code consumed and rebuilt EVERY successful response body,
                // doubling allocations for large incident/admin payloads. Peek only
                // the tiny prefix needed to recognize the backend's literal `null`.
                val contentLength = body.contentLength()
                if (contentLength == -1L || contentLength <= NULL_BODY_PEEK_BYTES) {
                    val preview = response.peekBody(NULL_BODY_PEEK_BYTES).string().trim()
                    if (preview == "null") {
                        val newBody = "[]".toResponseBody(body.contentType())
                        return@Interceptor response.newBuilder().body(newBody).build()
                    }
                }
            }
            response
        }

        val agapDns = object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                try {
                    val systemAddresses = Dns.SYSTEM.lookup(hostname)
                    if (systemAddresses.isNotEmpty()) {
                        return systemAddresses
                    }
                } catch (e: Exception) {
                    timber.log.Timber.w(e, "Dns.SYSTEM lookup failed for %s, falling back to direct network resolution", hostname)
                }

                if (hostname.equals("homeserver.tail8010bd.ts.net", ignoreCase = true) || hostname.endsWith(".ts.net")) {
                    val candidateIps = listOf("192.168.1.33", "100.116.160.67")
                    val fallbackList = candidateIps.mapNotNull { ip ->
                        try {
                            java.net.InetAddress.getByAddress(hostname, java.net.InetAddress.getByName(ip).address)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (fallbackList.isNotEmpty()) {
                        return fallbackList
                    }
                }
                throw java.net.UnknownHostException("Unable to resolve host: $hostname")
            }
        }

        return OkHttpClient.Builder()
            .cache(cache)
            .dns(agapDns)
            .addInterceptor { chain ->
                if (BuildConfig.OFFLINE_TEST) {
                    val token = runBlocking { tokenManager.getAuthToken() }
                    offlineTestDataSource.respond(chain.request(), token)
                } else {
                    chain.proceed(chain.request())
                }
            }
            .addInterceptor(transportPolicyInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(nullResponseSanitizerInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL.let { if (it.endsWith("/")) it else "$it/" })
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideAgapApiService(retrofit: Retrofit): AgapApiService = retrofit.create(AgapApiService::class.java)

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService = retrofit.create(AuthApiService::class.java)

    @Provides
    @Singleton
    fun provideIncidentApiService(retrofit: Retrofit): IncidentApiService = retrofit.create(IncidentApiService::class.java)

    @Provides
    @Singleton
    fun provideResponderApiService(retrofit: Retrofit): ResponderApiService = retrofit.create(ResponderApiService::class.java)

    @Provides
    @Singleton
    fun provideRoutingGraphApiService(retrofit: Retrofit): RoutingGraphApiService = retrofit.create(RoutingGraphApiService::class.java)

    @Provides
    @Singleton
    fun provideAgencyApiService(retrofit: Retrofit): AgencyApiService = retrofit.create(AgencyApiService::class.java)

    @Provides
    @Singleton
    fun provideMessageApiService(retrofit: Retrofit): MessageApiService = retrofit.create(MessageApiService::class.java)

    @Provides
    @Singleton
    fun provideNotificationApiService(retrofit: Retrofit): NotificationApiService = retrofit.create(NotificationApiService::class.java)

    @Provides
    @Singleton
    fun provideIncidentReportApiService(retrofit: Retrofit): com.travelingsalesman.agap.data.remote.IncidentReportApiService =
        retrofit.create(com.travelingsalesman.agap.data.remote.IncidentReportApiService::class.java)
}
