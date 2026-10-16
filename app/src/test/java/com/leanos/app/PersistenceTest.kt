package com.leanos.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PersistenceTest {
    private lateinit var context: Context
    private lateinit var db: LeanosDatabase
    private lateinit var repository: Repository

    private fun open() {
        db = Room.databaseBuilder(context, LeanosDatabase::class.java, "test-records.db").build()
        repository = Repository(db, db.records())
    }

    @Before
    fun before() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("test-records.db")
        open()
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase("test-records.db")
    }

    @Test
    fun onboardingAndRecordsSurviveDatabaseReopen() = runBlocking {
        val p = Profile(name = "Raziq")
        repository.profile(p, true)
        repository.habit(Habit(steps = 1300, notes = "Partial day"))
        db.close()
        open()
        val records = repository.records.first()
        assertEquals(p, records.profile)
        assertEquals(110.0, records.currentWeight(), 0.0)
        assertEquals(1, records.weights.size)
        assertEquals(1300, records.habits.single().steps)
    }

    @Test
    fun mealCrudUpdatesBudgetWithoutDoubleCounting() = runBlocking {
        val p = Profile(name = "Raziq")
        repository.profile(p, true)
        val m = Meal(name = "Nasi ayam", cost = 7.0)
        repository.meal(m)
        repository.meal(m.copy(name = "Nasi ayam large", cost = 9.0, protein = 30.0))
        var records = repository.records.first()
        assertEquals(1, records.meals.size)
        assertEquals(9.0, Calculations.budget(p, records.meals, records.expenses).spent, 0.0)
        assertEquals(30.0, Calculations.nutrition(records.meals)[1], 0.0)
        repository.delete(records.meals.single())
        records = repository.records.first()
        assertTrue(records.meals.isEmpty())
        assertEquals(0.0, Calculations.budget(p, records.meals, records.expenses).spent, 0.0)
    }

    @Test
    fun editingActivityMovesMinutesWithoutDoubleCounting() = runBlocking {
        repository.profile(Profile(name = "Raziq"), true)
        val session = Workout(minutes = 15)
        repository.workout(session)
        repository.workout(session.copy(minutes = 20))
        var r = repository.records.first()
        assertEquals(20, r.habits.single().walking)
        assertEquals(1, r.workouts.size)
        repository.delete(r.workouts.single())
        r = repository.records.first()
        assertEquals(0, r.habits.single().walking)
        assertTrue(r.workouts.isEmpty())
    }

    @Test
    fun invalidImportPreservesExistingRecordsAndValidBackupRoundTrips() = runBlocking {
        repository.profile(Profile(name = "Original"), true)
        val original = repository.records.first().backup()
        try {
            repository.restore(original.copy(profile = Profile(name = "")))
            fail("Expected validation failure")
        } catch (_: IllegalArgumentException) {}
        assertEquals("Original", repository.records.first().profile?.name)
        val encoded = json.encodeToString(original)
        repository.erase()
        assertNull(repository.records.first().profile)
        repository.restore(json.decodeFromString<Backup>(encoded))
        assertEquals(original, repository.records.first().backup())
    }
}
