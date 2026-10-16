package com.leanos.app

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlinx.serialization.Serializable

fun today(): String = LocalDate.now().toString()

@Serializable
data class Profile(
    val name: String = "",
    val age: Int = 19,
    val sex: String = "Prefer not to say",
    val height: Double = 169.0,
    val startingWeight: Double = 110.0,
    val currentWeight: Double = 110.0,
    val goalWeight: Double = 80.0,
    val waist: Double? = null,
    val activity: String = "Mostly sedentary",
    val averageSteps: Int = 3000,
    val exercises: String = "Walking, beginner strength training",
    val sleepHabits: String = "7–9 hours",
    val sugaryDrinks: String = "Occasionally",
    val foodPreferences: String = "Affordable Malaysian meals",
    val restrictions: String = "",
    val budget: Double = 350.0,
    val meals: String = "Brunch 10 AM; dinner 6–8 PM",
    val cooking: String = "No refrigerator or cooking appliance",
    val country: String = "Malaysia",
    val currency: String = "MYR",
    val stepTarget: Int = 4000,
    val waterTarget: Int = 2000,
    val proteinTarget: Double = 80.0,
    val sleepTarget: Double = 8.0,
    val startDate: String = today(),
    val calorieTarget: Double? = null,
    val carbTarget: Double? = null,
    val fatTarget: Double? = null,
) {
    fun validate() {
        require(name.isNotBlank() && name.length <= 80) { "Enter a name (up to 80 characters)." }
        require(age in 18..120) { "This personal adult tracker supports ages 18–120." }
        require(height.isFinite() && height in 80.0..250.0) { "Height must be 80–250 cm." }
        require(
            listOf(startingWeight, currentWeight, goalWeight).all {
                it.isFinite() && it in 25.0..500.0
            }
        ) {
            "Weights must be 25–500 kg."
        }
        require(goalWeight < startingWeight) { "Goal weight must be below starting weight." }
        require(waist == null || (waist.isFinite() && waist in 30.0..300.0)) {
            "Check waist measurement."
        }
        require(budget.isFinite() && budget in 0.0..100000.0) { "Check your budget." }
        require(
            stepTarget in 1..100000 &&
                averageSteps in 0..100000 &&
                waterTarget in 100..10000 &&
                proteinTarget.isFinite() &&
                proteinTarget in 1.0..500.0 &&
                sleepTarget.isFinite() &&
                sleepTarget in 1.0..24.0
        ) {
            "Check daily targets."
        }
        require(
            listOf(calorieTarget, carbTarget, fatTarget).all {
                it == null || (it.isFinite() && it > 0 && it <= 20000)
            }
        ) {
            "Optional nutrition targets must be positive, or left blank."
        }
        require(!LocalDate.parse(startDate).isAfter(LocalDate.now())) {
            "Start date cannot be in the future."
        }
    }
}

@Entity(tableName = "profile")
data class ProfileRow(@PrimaryKey val id: Int = 1, val payload: String)

@Serializable
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey val date: String = today(),
    val steps: Int = 0,
    val walking: Int = 0,
    val exercise: Int = 0,
    val water: Int = 0,
    val sugary: Int = 0,
    val sleep: Double = 0.0,
    val energy: Int = 3,
    val notes: String = "",
    val rest: Boolean = false,
) {
    fun validate() {
        require(!LocalDate.parse(date).isAfter(LocalDate.now())) {
            "Choose today or an earlier date."
        }
        require(
            steps in 0..100000 &&
                walking in 0..1440 &&
                exercise in 0..1440 &&
                water in 0..15000 &&
                sugary in 0..50 &&
                sleep.isFinite() &&
                sleep in 0.0..24.0 &&
                energy in 1..5 &&
                notes.length <= 5000
        ) {
            "Check habit values and units."
        }
    }
}

@Serializable
@Entity(tableName = "meals")
data class Meal(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val date: String = today(),
    val name: String,
    val category: String = "Brunch",
    val time: String = "10:00",
    val cost: Double = 0.0,
    val calories: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fibre: Double? = null,
    val notes: String = "",
    @ColumnInfo(defaultValue = "''") val photo: String = "",
    @ColumnInfo(defaultValue = "1") val servings: Double = 1.0,
) {
    fun validate() {
        require(id.isNotBlank() && name.isNotBlank() && name.length <= 200) { "Enter a meal name." }
        require(!LocalDate.parse(date).isAfter(LocalDate.now())) {
            "Meal date cannot be in the future."
        }
        java.time.LocalTime.parse(time)
        require(cost.isFinite() && cost in 0.0..100000.0) {
            "Enter a valid cost (0 for a free meal)."
        }
        require(
            listOf(calories, protein, carbs, fat, fibre).all {
                it == null || (it.isFinite() && it in 0.0..20000.0)
            }
        ) {
            "Nutrition must be positive, or left blank."
        }
        require(servings.isFinite() && servings in 0.1..1000.0) {
            "Servings must be between 0.1 and 1000."
        }
        require(photo.isEmpty() || isPhotoName(photo)) { "Invalid meal photo reference." }
        require(notes.length <= 10000 && category.isNotBlank() && category.length <= 80)
    }
}

fun Meal.withServings(count: Double): Meal {
    require(count.isFinite() && count in 0.1..1000.0 && servings > 0) {
        "Enter a valid serving quantity."
    }
    val factor = count / servings
    return copy(
        servings = count,
        calories = calories?.times(factor),
        protein = protein?.times(factor),
        carbs = carbs?.times(factor),
        fat = fat?.times(factor),
        fibre = fibre?.times(factor),
    )
}

@Serializable
@Entity(tableName = "weights")
data class Weight(
    @PrimaryKey val date: String = today(),
    val kg: Double,
    val waist: Double? = null,
) {
    fun validate() {
        require(!LocalDate.parse(date).isAfter(LocalDate.now())) {
            "Weight date cannot be in the future."
        }
        require(
            kg.isFinite() &&
                kg in 25.0..500.0 &&
                (waist == null || (waist.isFinite() && waist in 30.0..300.0))
        ) {
            "Check weight and waist."
        }
    }
}

@Serializable
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val date: String = today(),
    val title: String,
    val cost: Double,
) {
    fun validate() {
        require(
            id.isNotBlank() &&
                title.isNotBlank() &&
                title.length <= 200 &&
                cost.isFinite() &&
                cost in 0.0..100000.0
        ) {
            "Enter an expense name and valid amount."
        }
        require(!LocalDate.parse(date).isAfter(LocalDate.now()))
    }
}

data class BudgetSummary(
    val spent: Double,
    val remaining: Double,
    val daysRemaining: Int,
    val dailyAllowance: Double,
    val start: LocalDate,
    val end: LocalDate,
)

object Calculations {
    fun budget(
        profile: Profile,
        meals: List<Meal>,
        expenses: List<Expense>,
        date: LocalDate = LocalDate.now(),
    ): BudgetSummary {
        val anchor = LocalDate.parse(profile.startDate)
        val elapsed = max(0, ChronoUnit.DAYS.between(anchor, date).toInt())
        val start = anchor.plusDays((elapsed / 30 * 30).toLong())
        val end = start.plusDays(29)
        val costs =
            meals.filter { LocalDate.parse(it.date) in start..end }.map { it.cost } +
                expenses.filter { LocalDate.parse(it.date) in start..end }.map { it.cost }
        val spent = costs.sumOf { kotlin.math.round(it * 100).toLong() } / 100.0
        val days = ChronoUnit.DAYS.between(date, end).toInt().plus(1).coerceIn(1, 30)
        val remaining = profile.budget - spent
        return BudgetSummary(spent, remaining, days, max(0.0, remaining) / days, start, end)
    }

    fun progress(start: Double, current: Double, goal: Double): Float =
        if (start <= goal) 0f else ((start - current) / (start - goal)).toFloat().coerceIn(0f, 1f)

    fun nutrition(meals: List<Meal>): List<Double> =
        listOf(
            meals.sumOf { it.calories ?: 0.0 },
            meals.sumOf { it.protein ?: 0.0 },
            meals.sumOf { it.carbs ?: 0.0 },
            meals.sumOf { it.fat ?: 0.0 },
            meals.sumOf { it.fibre ?: 0.0 },
        )

    fun completion(h: Habit, p: Profile, meals: List<Meal>): Float =
        listOf(
                h.steps >= p.stepTarget,
                h.walking > 0 || h.exercise > 0 || h.rest,
                h.water >= p.waterTarget,
                h.sleep >= p.sleepTarget,
                meals.isNotEmpty(),
                meals.sumOf { it.protein ?: 0.0 } >= p.proteinTarget,
                h.sugary == 0,
            )
            .count { it } / 7f

    fun streak(
        habits: List<Habit>,
        p: Profile,
        meals: List<Meal>,
        date: LocalDate = LocalDate.now(),
    ): Int {
        var day = date
        if (
            habits.none {
                it.date == day.toString() &&
                    completion(it, p, meals.filter { m -> m.date == it.date }) >= 1f
            }
        )
            day = day.minusDays(1)
        var count = 0
        while (
            habits.any {
                it.date == day.toString() &&
                    completion(it, p, meals.filter { m -> m.date == it.date }) >= 1f
            }
        ) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}

@Serializable
data class Backup(
    val version: Int = 2,
    val profile: Profile?,
    val habits: List<Habit>,
    val meals: List<Meal>,
    val weights: List<Weight>,
    val expenses: List<Expense>,
    val workouts: List<Workout> = emptyList(),
    val favoriteMealIds: Set<String> = emptySet(),
) {
    fun validate() {
        require(version in 1..2) { "Unsupported backup version." }
        require(habits.size + meals.size + weights.size + expenses.size + workouts.size <= 100000) {
            "Backup too large."
        }
        require(favoriteMealIds.all { id -> meals.any { it.id == id } }) {
            "A favourite refers to a missing meal."
        }
        profile?.validate()
        habits.forEach { it.validate() }
        meals.forEach { it.validate() }
        weights.forEach { it.validate() }
        expenses.forEach { it.validate() }
        workouts.forEach { it.validate() }
        require(
            habits.distinctBy { it.date }.size == habits.size &&
                meals.distinctBy { it.id }.size == meals.size &&
                weights.distinctBy { it.date }.size == weights.size &&
                expenses.distinctBy { it.id }.size == expenses.size &&
                workouts.distinctBy { it.id }.size == workouts.size
        ) {
            "Duplicate records in backup."
        }
        require(
            profile != null ||
                (habits.isEmpty() &&
                    meals.isEmpty() &&
                    weights.isEmpty() &&
                    expenses.isEmpty() &&
                    workouts.isEmpty())
        ) {
            "Backup records require a profile."
        }
    }
}

@Serializable
@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val date: String = today(),
    val name: String = "Walk",
    val kind: String = "Walking",
    val minutes: Int = 15,
    @ColumnInfo(defaultValue = "0") val sets: Int = 0,
    @ColumnInfo(defaultValue = "0") val reps: Int = 0,
    val loadKg: Double? = null,
) {
    fun validate() {
        require(
            id.isNotBlank() &&
                name.isNotBlank() &&
                name.length <= 200 &&
                kind in listOf("Walking", "Strength", "Other") &&
                minutes in 1..1440
        ) {
            "Enter a name, Walking/Strength/Other type, and 1–1440 minutes."
        }
        require(
            sets in 0..100 &&
                reps in 0..1000 &&
                (loadKg == null || (loadKg.isFinite() && loadKg in 0.0..1000.0))
        ) {
            "Check sets, reps and load."
        }
        require(!LocalDate.parse(date).isAfter(LocalDate.now())) {
            "Session date cannot be in the future."
        }
    }
}
