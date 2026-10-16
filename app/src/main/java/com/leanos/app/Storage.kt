package com.leanos.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@HiltAndroidApp class LeanosApplication : Application()

@Dao
interface RecordsDao {
    @Query("SELECT * FROM profile WHERE id=1") fun profile(): Flow<ProfileRow?>

    @Query("SELECT * FROM habits ORDER BY date DESC") fun habits(): Flow<List<Habit>>

    @Query("SELECT * FROM meals ORDER BY date DESC, time DESC") fun meals(): Flow<List<Meal>>

    @Query("SELECT * FROM weights ORDER BY date DESC") fun weights(): Flow<List<Weight>>

    @Query("SELECT * FROM expenses ORDER BY date DESC") fun expenses(): Flow<List<Expense>>

    @Query("SELECT * FROM workouts ORDER BY date DESC") fun workouts(): Flow<List<Workout>>

    @Query("SELECT * FROM workouts WHERE id=:id") suspend fun workout(id: String): Workout?

    @Query("SELECT * FROM habits WHERE date=:date") suspend fun habit(date: String): Habit?

    @Upsert suspend fun putWorkout(value: Workout)

    @Delete suspend fun deleteWorkout(value: Workout)

    @Query("DELETE FROM workouts") suspend fun clearWorkouts()

    @Upsert suspend fun putProfile(value: ProfileRow)

    @Upsert suspend fun putHabit(value: Habit)

    @Upsert suspend fun putMeal(value: Meal)

    @Upsert suspend fun putWeight(value: Weight)

    @Upsert suspend fun putExpense(value: Expense)

    @Delete suspend fun deleteMeal(value: Meal)

    @Delete suspend fun deleteWeight(value: Weight)

    @Delete suspend fun deleteExpense(value: Expense)

    @Query("DELETE FROM profile") suspend fun clearProfile()

    @Query("DELETE FROM habits") suspend fun clearHabits()

    @Query("DELETE FROM meals") suspend fun clearMeals()

    @Query("DELETE FROM weights") suspend fun clearWeights()

    @Query("DELETE FROM expenses") suspend fun clearExpenses()
}

@Database(
    entities =
        [
            ProfileRow::class,
            Habit::class,
            Meal::class,
            Weight::class,
            Expense::class,
            Workout::class,
        ],
    version = 2,
    exportSchema = true,
)
abstract class LeanosDatabase : RoomDatabase() {
    abstract fun records(): RecordsDao
}

val MIGRATION_1_2 =
    object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE meals ADD COLUMN photo TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE meals ADD COLUMN servings REAL NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE workouts ADD COLUMN sets INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE workouts ADD COLUMN reps INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE workouts ADD COLUMN loadKg REAL DEFAULT NULL")
        }
    }

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun db(app: Application): LeanosDatabase =
        Room.databaseBuilder(app, LeanosDatabase::class.java, "leanos.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun dao(db: LeanosDatabase) = db.records()
}

val json = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
}

data class Records(
    val profile: Profile? = null,
    val habits: List<Habit> = emptyList(),
    val meals: List<Meal> = emptyList(),
    val weights: List<Weight> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val workouts: List<Workout> = emptyList(),
) {
    fun backup() =
        Backup(
            profile = profile,
            habits = habits,
            meals = meals,
            weights = weights,
            expenses = expenses,
            workouts = workouts,
        )

    fun currentWeight(): Double = weights.firstOrNull()?.kg ?: profile?.currentWeight ?: 0.0
}

@Singleton
class Repository @Inject constructor(private val db: LeanosDatabase, private val dao: RecordsDao) {
    private val base =
        combine(dao.profile(), dao.habits(), dao.meals(), dao.weights(), dao.expenses()) {
            p,
            h,
            m,
            w,
            e ->
            Records(p?.let { json.decodeFromString<Profile>(it.payload) }, h, m, w, e)
        }
    val records = combine(base, dao.workouts()) { r, w -> r.copy(workouts = w) }

    suspend fun profile(p: Profile, initial: Boolean) {
        p.validate()
        db.withTransaction {
            val previous = dao.weights().first().firstOrNull()?.kg
            dao.putProfile(ProfileRow(payload = json.encodeToString(p)))
            if (initial)
                dao.putWeight(Weight(date = p.startDate, kg = p.currentWeight, waist = p.waist))
            else if (previous != p.currentWeight)
                dao.putWeight(Weight(kg = p.currentWeight, waist = p.waist))
        }
    }

    suspend fun habit(h: Habit) {
        h.validate()
        dao.putHabit(h)
    }

    suspend fun meal(m: Meal) {
        m.validate()
        dao.putMeal(m)
    }

    suspend fun weight(w: Weight) {
        w.validate()
        dao.putWeight(w)
    }

    suspend fun expense(e: Expense) {
        e.validate()
        dao.putExpense(e)
    }

    private suspend fun sessionDelta(w: Workout, sign: Int) {
        val h = dao.habit(w.date) ?: Habit(date = w.date)
        val next =
            if (w.kind == "Walking")
                h.copy(walking = (h.walking + sign * w.minutes).coerceAtLeast(0))
            else h.copy(exercise = (h.exercise + sign * w.minutes).coerceAtLeast(0))
        next.validate()
        dao.putHabit(next)
    }

    suspend fun workout(w: Workout) {
        w.validate()
        db.withTransaction {
            dao.workout(w.id)?.let { sessionDelta(it, -1) }
            sessionDelta(w, 1)
            dao.putWorkout(w)
        }
    }

    suspend fun delete(w: Workout) =
        db.withTransaction {
            dao.workout(w.id)?.let {
                sessionDelta(it, -1)
                dao.deleteWorkout(it)
            }
        }

    suspend fun delete(m: Meal) = dao.deleteMeal(m)

    suspend fun delete(w: Weight) = dao.deleteWeight(w)

    suspend fun delete(e: Expense) = dao.deleteExpense(e)

    private suspend fun clear() {
        dao.clearProfile()
        dao.clearHabits()
        dao.clearMeals()
        dao.clearWeights()
        dao.clearExpenses()
        dao.clearWorkouts()
    }

    suspend fun erase() = db.withTransaction { clear() }

    suspend fun restore(backup: Backup) {
        backup.validate()
        db.withTransaction {
            clear()
            backup.profile?.let { dao.putProfile(ProfileRow(payload = json.encodeToString(it))) }
            backup.habits.forEach { dao.putHabit(it) }
            backup.meals.forEach { dao.putMeal(it) }
            backup.weights.forEach { dao.putWeight(it) }
            backup.expenses.forEach { dao.putExpense(it) }
            backup.workouts.forEach { dao.putWorkout(it) }
        }
    }
}

private val Context.preferences by preferencesDataStore("leanos_preferences")

@Singleton
class Preferences @Inject constructor(app: Application) {
    private val store = app.preferences
    private val favoritesKey = stringSetPreferencesKey("favorite_meal_ids")
    val favorites = store.data.map { it[favoritesKey] ?: emptySet() }

    suspend fun favorite(id: String) {
        store.edit { prefs ->
            val ids = prefs[favoritesKey] ?: emptySet()
            prefs[favoritesKey] = if (id in ids) ids - id else ids + id
        }
    }

    suspend fun restoreFavorites(ids: Set<String>) {
        store.edit { it[favoritesKey] = ids }
    }

    private val themeKey = stringPreferencesKey("theme")
    private val endpointKey = stringPreferencesKey("scanner_endpoint")
    val theme = store.data.map { it[themeKey] ?: "Dark" }
    val endpoint = store.data.map { it[endpointKey] ?: "" }

    suspend fun theme(value: String) {
        require(value in listOf("Dark", "Light", "System"))
        store.edit { it[themeKey] = value }
    }

    suspend fun endpoint(value: String) {
        require(
            value.isBlank() ||
                (android.net.Uri.parse(value).let {
                    it.scheme == "https" &&
                        !it.host.isNullOrBlank() &&
                        it.userInfo == null &&
                        it.query == null &&
                        it.fragment == null
                })
        ) {
            "Use an HTTPS backend URL without credentials or query parameters."
        }
        store.edit { it[endpointKey] = value.trimEnd('/') }
    }

    suspend fun reset() {
        store.edit { it.clear() }
    }
}
