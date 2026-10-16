package com.leanos.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CalculationsTest {
    private val p = Profile(name = "Raziq", startDate = "2026-09-01")

    @Test
    fun budgetUsesThirtyDayWindowsAndIncludesToday() {
        val meals =
            listOf(
                Meal(id = "a", date = "2026-09-01", name = "Nasi ayam", cost = 7.5),
                Meal(id = "b", date = "2026-09-30", name = "Free meal", cost = 0.0),
                Meal(id = "c", date = "2026-10-01", name = "Next cycle", cost = 20.0),
            )
        val b =
            Calculations.budget(
                p,
                meals,
                listOf(Expense(date = "2026-09-05", title = "Fruit", cost = 2.5)),
                LocalDate.parse("2026-09-30"),
            )
        assertEquals(10.0, b.spent, 0.001)
        assertEquals(340.0, b.remaining, 0.001)
        assertEquals(1, b.daysRemaining)
        assertEquals(340.0, b.dailyAllowance, 0.001)
        val next = Calculations.budget(p, meals, emptyList(), LocalDate.parse("2026-10-01"))
        assertEquals(20.0, next.spent, 0.001)
        assertEquals(30, next.daysRemaining)
    }

    @Test
    fun budgetClampsAllowanceAndCountsCurrencyInCents() {
        val meals =
            listOf(
                Meal(name = "Meal", date = "2026-09-01", cost = 400.10),
                Meal(name = "Drink", date = "2026-09-01", cost = 0.20),
            )
        val b = Calculations.budget(p, meals, emptyList(), LocalDate.parse("2026-09-01"))
        assertEquals(400.30, b.spent, 0.00001)
        assertTrue(b.remaining < 0)
        assertEquals(0.0, b.dailyAllowance, 0.0)
    }

    @Test
    fun progressUsesMeasurementsAndClamps() {
        assertEquals(.5f, Calculations.progress(110.0, 95.0, 80.0))
        assertEquals(0f, Calculations.progress(110.0, 115.0, 80.0))
        assertEquals(1f, Calculations.progress(110.0, 75.0, 80.0))
        assertEquals(0f, Calculations.progress(80.0, 80.0, 80.0))
    }

    @Test
    fun nutritionAllowsMissingValues() {
        val totals =
            Calculations.nutrition(
                listOf(
                    Meal(name = "Rice", calories = 200.0, carbs = 40.0),
                    Meal(name = "Egg", calories = 80.0, protein = 6.0),
                    Meal(name = "Unknown"),
                )
            )
        assertEquals(listOf(280.0, 6.0, 40.0, 0.0, 0.0), totals)
    }

    @Test
    fun invalidNumbersAndBackupsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            p.copy(budget = Double.NaN).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            Meal(name = "", cost = -1.0).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            Backup(
                    profile = p,
                    habits = emptyList(),
                    meals = listOf(Meal(id = "a", name = "A"), Meal(id = "a", name = "B")),
                    weights = emptyList(),
                    expenses = emptyList(),
                )
                .validate()
        }
    }

    @Test
    fun aiValidationRejectsInvalidRangesAndRequiresUncertainty() {
        val component =
            FoodComponent(
                "Rice",
                listOf("rice"),
                "1 bowl, confirm",
                100.0,
                200.0,
                130.0,
                260.0,
                3.0,
                30.0,
                1.0,
                1.0,
            )
        val result =
            FoodAnalysis(
                "Rice",
                listOf(component),
                "Boiled",
                emptyList(),
                "medium",
                "Portion and oil unknown. Confirm size.",
                "image_estimate",
            )
        result.validate()
        assertEquals(195.0, result.meal().calories!!, 0.001)
        assertThrows(IllegalArgumentException::class.java) {
            result.copy(components = listOf(component.copy(gramsMin = 300.0))).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            result.copy(uncertainty = "").validate()
        }
    }

    @Test
    fun streakUsesConsecutiveCompletedDaysRatherThanEntryCount() {
        val day = LocalDate.now()
        val profile = Profile(name = "Raziq")
        fun h(d: LocalDate) =
            Habit(
                date = d.toString(),
                steps = profile.stepTarget,
                walking = 20,
                water = profile.waterTarget,
                sleep = 8.0,
            )
        val habits = listOf(h(day.minusDays(1)), h(day.minusDays(2)), h(day.minusDays(4)))
        val meals = habits.map { Meal(date = it.date, name = "Protein meal", protein = 100.0) }
        assertEquals(2, Calculations.streak(habits, profile, meals, day))
    }

    @Test
    fun servingChangesScaleNutritionWithoutChangingCostOrUnknownValues() {
        val meal = Meal(name = "Rice", cost = 6.0, calories = 250.0, protein = 5.0)
        val two = meal.withServings(2.0)
        two.validate()
        assertEquals(500.0, two.calories!!, 0.0)
        assertEquals(10.0, two.protein!!, 0.0)
        assertEquals(6.0, two.cost, 0.0)
        assertNull(two.fat)
        assertEquals(250.0, two.withServings(1.0).calories!!, 0.0)
        assertThrows(IllegalArgumentException::class.java) { meal.withServings(0.0) }
    }
}
