package com.travelingsalesman.agap.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.travelingsalesman.agap.BuildConfig
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
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
        tokenManager: AuthTokenManager,
        serverConfigManager: ServerConfigManager
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
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

        val dynamicUrlInterceptor = Interceptor { chain ->
            var request = chain.request()
            val customUrl = if (serverConfigManager.isServerUrlLoaded) {
                serverConfigManager.currentServerUrl
            } else {
                runBlocking { serverConfigManager.getServerUrl() }
            }

            if (!customUrl.isNullOrEmpty()) {
                try {
                    val targetHttpUrl = customUrl.toHttpUrlOrNull()
                    if (targetHttpUrl != null && (request.url.host != targetHttpUrl.host || request.url.scheme != targetHttpUrl.scheme || request.url.port != targetHttpUrl.port)) {
                        val newUrlBuilder = request.url.newBuilder()
                            .scheme(targetHttpUrl.scheme)
                            .host(targetHttpUrl.host)
                            .port(targetHttpUrl.port)

                        val customPathSegments = targetHttpUrl.pathSegments.filter { it.isNotEmpty() }
                        if (customPathSegments.isNotEmpty()) {
                            val originalSegments = request.url.pathSegments
                            val isWsRoute = originalSegments.lastOrNull() == "ws" || originalSegments.contains("ws")

                            if (!isWsRoute) {
                                val subPath = originalSegments.dropWhile { it == "api" || it == "v1" }

                                repeat(request.url.pathSize) {
                                    newUrlBuilder.removePathSegment(0)
                                }
                                customPathSegments.forEach(newUrlBuilder::addPathSegment)
                                subPath.forEach(newUrlBuilder::addPathSegment)
                            }
                        }

                        request = request.newBuilder().url(newUrlBuilder.build()).build()
                    }
                } catch (_: Exception) {
                    // Keep the original URL if the custom base URL is malformed.
                }
            }
            chain.proceed(request)
        }

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
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
            chain.proceed(requestBuilder.build())
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

        return OkHttpClient.Builder()
            .addInterceptor(transportPolicyInterceptor)
            .addInterceptor(dynamicUrlInterceptor)
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
