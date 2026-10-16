package com.leanos.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate

fun exerciseNames(kind: String) =
    when (kind) {
        "Walking" -> listOf("Walk", "Brisk walk", "Treadmill walk", "Hike")
        "Strength" ->
            listOf(
                "Beginner strength",
                "Chair squats",
                "Wall push-ups",
                "Bodyweight squats",
                "Dumbbell rows",
                "Resistance bands",
                "Plank",
            )
        else -> listOf("Cycling", "Swimming", "Jogging", "Yoga", "Stretching", "Sport")
    }

fun choices(field: Field, values: Map<String, String>): List<String> =
    when (field.key) {
        "kind" -> listOf("Walking", "Strength", "Other")
        "workoutName" -> exerciseNames(values["kind"] ?: "Walking") + "Custom exercise"
        "category" -> listOf("Breakfast", "Brunch", "Lunch", "Dinner", "Snack", "Drink")
        "sex" -> listOf("Prefer not to say", "Male", "Female", "Other")
        "activity" ->
            listOf("Mostly sedentary", "Lightly active", "Moderately active", "Very active")
        "sugar" -> listOf("Never", "Occasionally", "Once daily", "Several times daily")
        "sleep" ->
            if (!field.numeric)
                listOf(
                    "Less than 6 hours",
                    "6–7 hours",
                    "7–9 hours",
                    "More than 9 hours",
                    "Irregular",
                )
            else emptyList()
        "cooking" ->
            listOf(
                "No refrigerator or cooking appliance",
                "Refrigerator only",
                "Kettle only",
                "Microwave access",
                "Shared kitchen",
                "Full kitchen and refrigerator",
            )
        else -> emptyList()
    }

fun stepSize(key: String): Double? =
    when (key) {
        "minutes",
        "walking",
        "exercise" -> 5.0
        "steps",
        "stepTarget" -> 500.0
        "water",
        "waterTarget" -> 250.0
        "sugary",
        "sets",
        "reps",
        "age" -> 1.0
        "start",
        "current",
        "goal",
        "kg",
        "waist",
        "loadKg",
        "sleep",
        "sleepTarget" -> .5
        "height" -> 1.0
        "servings" -> .5
        "proteinTarget",
        "carbTarget",
        "fatTarget" -> 5.0
        "calorieTarget" -> 100.0
        else -> null
    }

@Composable
fun ChoiceControl(field: Field, values: MutableMap<String, String>, options: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    var custom by rememberSaveable(field.key, values["kind"]) { mutableStateOf(false) }
    if (field.key == "workoutName" && custom) {
        OutlinedTextField(
            values[field.key].orEmpty(),
            { values[field.key] = it },
            label = { Text("Custom exercise name") },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = {
                custom = false
                values[field.key] = options.first()
            }
        ) {
            Text("Choose from exercises")
        }
        return
    }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    field.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    values[field.key].orEmpty().ifBlank { "Choose an option" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(Icons.Outlined.ExpandMore, "Choose ${field.label}")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            (options + values[field.key].orEmpty())
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            if (field.key == "workoutName" && option == "Custom exercise") {
                                custom = true
                                values[field.key] = ""
                            } else values[field.key] = option
                            if (field.key == "kind")
                                values["workoutName"] = exerciseNames(option).first()
                            expanded = false
                        },
                        trailingIcon =
                            if (values[field.key] == option) ({ Icon(Icons.Outlined.Check, null) })
                            else null,
                    )
                }
        }
    }
}

@Composable
fun NumberButtons(field: Field, values: MutableMap<String, String>, step: Double) {
    val minimum =
        when (field.key) {
            "servings" -> .1
            "minutes",
            "stepTarget",
            "proteinTarget",
            "carbTarget",
            "fatTarget",
            "sleepTarget" -> 1.0
            "waterTarget" -> 100.0
            "kg",
            "start",
            "current",
            "goal" -> 25.0
            "height" -> 80.0
            "age" -> 18.0
            else -> 0.0
        }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Adjust by ${if(step%1.0==0.0) step.toInt().toString() else step.toString()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedIconButton(
                enabled = (values[field.key]?.toDoubleOrNull() ?: 0.0) > minimum,
                onClick = {
                    val next =
                        ((values[field.key]?.toDoubleOrNull() ?: 0.0) - step).coerceAtLeast(minimum)
                    values[field.key] =
                        if (step % 1.0 == 0.0) next.toInt().toString() else next.fmt(1)
                },
            ) {
                Icon(Icons.Outlined.Remove, "Decrease ${field.label}")
            }
            FilledTonalIconButton(
                onClick = {
                    val next = (values[field.key]?.toDoubleOrNull() ?: 0.0) + step
                    values[field.key] =
                        if (step % 1.0 == 0.0) next.toInt().toString() else next.fmt(1)
                }
            ) {
                Icon(Icons.Outlined.Add, "Increase ${field.label}")
            }
        }
    }
}

@Composable
fun ExercisePreferences(field: Field, values: MutableMap<String, String>) {
    SectionTitle(field.label, "Choose as many as you like")
    val selected =
        values[field.key].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("Walking", "Beginner strength training", "Cycling", "Swimming", "Yoga").forEach {
            option ->
            val active = selected.any { it.equals(option, true) }
            FilterChip(
                active,
                {
                    values[field.key] =
                        (if (active) selected.filterNot { it.equals(option, true) }
                            else selected + option)
                            .joinToString(", ")
                },
                label = { Text(option) },
            )
        }
    }
}

@Composable
fun ChooseDay(value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val date = LocalDate.parse(value)
            android.app
                .DatePickerDialog(
                    context,
                    { _, y, m, d -> onChange(LocalDate.of(y, m + 1, d).toString()) },
                    date.year,
                    date.monthValue - 1,
                    date.dayOfMonth,
                )
                .apply { datePicker.maxDate = System.currentTimeMillis() }
                .show()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Choose another day")
    }
}
