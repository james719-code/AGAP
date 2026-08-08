package com.travelingsalesman.agap.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.travelingsalesman.agap.BuildConfig
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.remote.AgencyApiService
import com.travelingsalesman.agap.data.remote.AuthApiService
import com.travelingsalesman.agap.data.remote.IncidentApiService
import com.travelingsalesman.agap.data.remote.MessageApiService
import com.travelingsalesman.agap.data.remote.ResponderApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

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
        serverConfigManager: com.travelingsalesman.agap.data.local.ServerConfigManager
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val dynamicUrlInterceptor = Interceptor { chain ->
            var request = chain.request()
            val customUrl = serverConfigManager.currentServerUrl ?: runBlocking { serverConfigManager.serverUrl.firstOrNull() }
            if (!customUrl.isNullOrEmpty()) {
                try {
                    val targetHttpUrl = customUrl.toHttpUrlOrNull()
                    if (targetHttpUrl != null) {
                        val newUrlBuilder = request.url.newBuilder()
                            .scheme(targetHttpUrl.scheme)
                            .host(targetHttpUrl.host)
                            .port(targetHttpUrl.port)

                        val customPathSegments = targetHttpUrl.pathSegments.filter { it.isNotEmpty() }
                        if (customPathSegments.isNotEmpty()) {
                            val originalSegments = request.url.pathSegments
                            val subPath = originalSegments.dropWhile { it == "api" || it == "v1" }
                            
                            // Remove existing path segments and rebuild with custom base path + subPath
                            val totalSegments = request.url.pathSize
                            for (i in 0 until totalSegments) {
                                newUrlBuilder.removePathSegment(0)
                            }
                            for (seg in customPathSegments) {
                                newUrlBuilder.addPathSegment(seg)
                            }
                            for (seg in subPath) {
                                newUrlBuilder.addPathSegment(seg)
                            }
                        }

                        request = request.newBuilder().url(newUrlBuilder.build()).build()
                    }
                } catch (e: Exception) {
                    // Fallback to original url
                }
            }
            chain.proceed(request)
        }

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = runBlocking { tokenManager.authToken.firstOrNull() }
            
            val requestBuilder = original.newBuilder()
            if (!token.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }

        return OkHttpClient.Builder()
            .addInterceptor(dynamicUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
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
    fun provideAgapApiService(retrofit: Retrofit): AgapApiService {
        return retrofit.create(AgapApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService {
        return retrofit.create(AuthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideIncidentApiService(retrofit: Retrofit): IncidentApiService {
        return retrofit.create(IncidentApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideResponderApiService(retrofit: Retrofit): ResponderApiService {
        return retrofit.create(ResponderApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAgencyApiService(retrofit: Retrofit): AgencyApiService {
        return retrofit.create(AgencyApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideMessageApiService(retrofit: Retrofit): MessageApiService {
        return retrofit.create(MessageApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideIncidentReportApiService(retrofit: Retrofit): com.travelingsalesman.agap.data.remote.IncidentReportApiService {
        return retrofit.create(com.travelingsalesman.agap.data.remote.IncidentReportApiService::class.java)
    }
}
