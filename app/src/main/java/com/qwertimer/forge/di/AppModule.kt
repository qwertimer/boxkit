package com.qwertimer.forge.di

import android.content.Context
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.qwertimer.forge.BuildConfig
import com.qwertimer.forge.data.db.DiaryDao
import com.qwertimer.forge.data.db.ExerciseDao
import com.qwertimer.forge.data.db.FoodDao
import com.qwertimer.forge.data.db.ForgeDatabase
import com.qwertimer.forge.data.db.WorkoutDao
import com.qwertimer.forge.data.remote.gemini.GeminiApi
import com.qwertimer.forge.data.remote.off.OpenFoodFactsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ForgeDatabase =
        Room.databaseBuilder(context, ForgeDatabase::class.java, ForgeDatabase.NAME)
            // No fallbackToDestructiveMigration: a missing migration should fail loudly in
            // development rather than quietly delete somebody's training history.
            .addMigrations(ForgeDatabase.MIGRATION_1_2)
            .build()

    @Provides fun provideFoodDao(db: ForgeDatabase): FoodDao = db.foodDao()

    @Provides fun provideDiaryDao(db: ForgeDatabase): DiaryDao = db.diaryDao()

    @Provides fun provideExerciseDao(db: ForgeDatabase): ExerciseDao = db.exerciseDao()

    @Provides fun provideWorkoutDao(db: ForgeDatabase): WorkoutDao = db.workoutDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        // Open Food Facts is community-maintained and plays fast and loose with types: a numeric
        // field can arrive quoted, and vice versa. Lenient parsing is the difference between a
        // working scanner and a crash on somebody's tin of beans.
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // Open Food Facts asks every client to identify itself, and rate-limits those that do not.
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
            chain.proceed(request)
        }
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideOpenFoodFactsApi(client: OkHttpClient, json: Json): OpenFoodFactsApi =
        Retrofit.Builder()
            .baseUrl(OpenFoodFactsApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()
            .create(OpenFoodFactsApi::class.java)

    @Provides
    @Singleton
    fun provideGeminiApi(client: OkHttpClient, json: Json): GeminiApi =
        Retrofit.Builder()
            .baseUrl(GeminiApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()
            .create(GeminiApi::class.java)

    private val USER_AGENT = "Forge/${BuildConfig.VERSION_NAME} (Android; github.com/qwertimer)"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
}
