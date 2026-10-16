package com.leanos.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class Field(val key: String, val label: String, val initial: String, val numeric: Boolean = false)

@Composable
fun fieldValues(fields: List<Field>): MutableMap<String, String> =
    rememberSaveable(
        saver =
            mapSaver(
                save = { it.toMap() },
                restore = { saved ->
                    mutableStateMapOf<String, String>().apply {
                        saved.forEach { (key, value) -> put(key, value as String) }
                    }
                },
            )
    ) {
        mutableStateMapOf<String, String>().apply { fields.forEach { put(it.key, it.initial) } }
    }

fun Map<String, String>.s(key: String) = getValue(key).trim()

fun Map<String, String>.d(key: String) =
    s(key).toDoubleOrNull()?.takeIf { it.isFinite() } ?: error("Enter a valid number for $key.")

fun Map<String, String>.i(key: String) =
    s(key).toIntOrNull() ?: error("Enter a whole number for $key.")

fun Map<String, String>.optional(key: String) = if (s(key).isBlank()) null else d(key)

@Composable
fun Input(field: Field, values: MutableMap<String, String>) {
    OutlinedTextField(
        value = values[field.key] ?: "",
        onValueChange = { if (it.length <= 10000) values[field.key] = it },
        label = { Text(field.label) },
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (field.numeric) KeyboardType.Decimal else KeyboardType.Text
            ),
        singleLine = field.numeric,
    )
}

@Composable
fun EditorDialog(
    title: String,
    fields: List<Field>,
    vm: MainViewModel,
    onDismiss: () -> Unit,
    save: suspend (Map<String, String>) -> Unit,
    onSaved: () -> Unit = onDismiss,
    extra: @Composable () -> Unit = {},
) {
    val values = fieldValues(fields)
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                fields.forEach { Input(it, values) }
                extra()
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = { vm.run(done = onSaved) { save(values.toMap()) } },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save record")
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun MealEditor(vm: MainViewModel, meal: Meal?, onSaved: (() -> Unit)? = null, close: () -> Unit) {
    val m = remember { meal ?: Meal(name = "") }
    val fields =
        listOf(
            Field("name", "Food name", m.name),
            Field("category", "Category (Brunch, Dinner, Snack…)", m.category),
            Field("date", "Date · YYYY-MM-DD", m.date),
            Field("time", "Time · HH:mm", m.time),
            Field("cost", "Cost · RM (0 = free)", m.cost.toString(), true),
            Field("calories", "Calories · kcal (optional)", m.calories?.toString() ?: "", true),
            Field("protein", "Protein · g (optional)", m.protein?.toString() ?: "", true),
            Field("carbs", "Carbohydrates · g (optional)", m.carbs?.toString() ?: "", true),
            Field("fat", "Fat · g (optional)", m.fat?.toString() ?: "", true),
            Field("fibre", "Fibre · g (optional)", m.fibre?.toString() ?: "", true),
            Field("notes", "Ingredients, portions & notes", m.notes),
        )
    EditorDialog(
        if (meal == null) "Log a meal" else "Review & edit meal",
        fields,
        vm,
        close,
        save = { v ->
            vm.repository.meal(
                m.copy(
                    name = v.s("name"),
                    category = v.s("category"),
                    date = v.s("date"),
                    time = v.s("time"),
                    cost = v.d("cost"),
                    calories = v.optional("calories"),
                    protein = v.optional("protein"),
                    carbs = v.optional("carbs"),
                    fat = v.optional("fat"),
                    fibre = v.optional("fibre"),
                    notes = v.s("notes"),
                )
            )
        },
        onSaved = onSaved ?: close,
        extra = {
            Text(
                "Nutrition is optional. Photo estimates are approximate; confirm portions and adjust ingredients before saving.",
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
}

@Composable
fun WeightEditor(vm: MainViewModel, weight: Weight?, close: () -> Unit) {
    val fields =
        listOf(
            Field("date", "Date · YYYY-MM-DD", weight?.date ?: today()),
            Field("kg", "Weight · kg", weight?.kg?.toString() ?: "", true),
            Field("waist", "Waist · cm (optional)", weight?.waist?.toString() ?: "", true),
        )
    EditorDialog(
        "Weight & body",
        fields,
        vm,
        close,
        save = { v ->
            val next = Weight(v.s("date"), v.d("kg"), v.optional("waist"))
            next.validate()
            if (weight != null && next.date != weight.date)
                error(
                    "To change the date, delete this record and add a new weigh-in. This prevents overwriting history."
                )
            vm.repository.weight(next)
        },
    )
}

@Composable
fun ExpenseEditor(vm: MainViewModel, expense: Expense?, close: () -> Unit) {
    val e = remember { expense ?: Expense(title = "", cost = 0.0) }
    EditorDialog(
        "Food expense",
        listOf(
            Field("title", "Expense (do not duplicate a logged meal)", e.title),
            Field("date", "Date · YYYY-MM-DD", e.date),
            Field("cost", "Cost · RM", e.cost.toString(), true),
        ),
        vm,
        close,
        save = {
            vm.repository.expense(
                e.copy(title = it.s("title"), date = it.s("date"), cost = it.d("cost"))
            )
        },
    )
}

@Composable
fun ProfileEditor(vm: MainViewModel, p: Profile, onboarding: Boolean, close: () -> Unit) {
    val groups =
        listOf(
            listOf(
                Field("name", "Your name or nickname", p.name),
                Field("age", "Age", p.age.toString(), true),
                Field("sex", "Sex (optional)", p.sex),
                Field("height", "Height · cm", p.height.toString(), true),
                Field("start", "Starting weight · kg", p.startingWeight.toString(), true),
                Field("current", "Current weight · kg", p.currentWeight.toString(), true),
                Field("goal", "Goal weight · kg", p.goalWeight.toString(), true),
                Field("waist", "Waist · cm (optional)", p.waist?.toString() ?: "", true),
            ),
            listOf(
                Field("activity", "Daily activity level", p.activity),
                Field("steps", "Average daily steps", p.averageSteps.toString(), true),
                Field("exercise", "Preferred exercises", p.exercises),
                Field("sleep", "Sleep habits", p.sleepHabits),
                Field("sugar", "Sugary-drink habits", p.sugaryDrinks),
                Field("stepTarget", "Daily step target", p.stepTarget.toString(), true),
                Field("waterTarget", "Water target · ml", p.waterTarget.toString(), true),
                Field("proteinTarget", "Protein target · g", p.proteinTarget.toString(), true),
                Field("sleepTarget", "Sleep target · hours", p.sleepTarget.toString(), true),
            ),
            listOf(
                Field("foods", "Food preferences", p.foodPreferences),
                Field("restrictions", "Food restrictions (optional)", p.restrictions),
                Field("budget", "Food budget per 30 days · RM", p.budget.toString(), true),
                Field("meals", "Meal schedule", p.meals),
                Field("cooking", "Cooking & food storage access", p.cooking),
                Field("country", "Country", p.country),
            ),
        )
    val values = fieldValues(groups.flatten())
    var step by rememberSaveable { mutableIntStateOf(0) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (onboarding) {
            Label("LEANOS   /   ${step+1} OF 3")
            Heading(
                listOf("A fresh start.", "Make it your pace.", "Built for your life.")[step],
                "Your body. Your progress. Your rules.",
            )
            LinearProgressIndicator(
                progress = { (step + 1) / 3f },
                modifier = Modifier.fillMaxWidth(),
            )
        } else Heading("Your profile", "Update goals and daily targets")
        Text(
            listOf("About you", "Your daily rhythm", "Food & budget")[step],
            style = MaterialTheme.typography.titleLarge,
        )
        groups[step].forEach { Input(it, values) }
        if (step == 0)
            Text(
                "Adult personal tracking, not medical advice. Choose a gradual goal and get professional advice for health concerns.",
                style = MaterialTheme.typography.bodySmall,
            )
        if (step == 2)
            Text(
                "Metric units · MYR. Budget cycles begin ${p.startDate}. No account required. Your records stay on this device; only photos you submit go to your scanner backend.",
                style = MaterialTheme.typography.bodySmall,
            )
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = {
                if (step < 2) step++
                else
                    vm.run(done = close) {
                        val v = values.toMap()
                        val profile =
                            p.copy(
                                name = v.s("name"),
                                age = v.i("age"),
                                sex = v.s("sex"),
                                height = v.d("height"),
                                startingWeight = v.d("start"),
                                currentWeight = v.d("current"),
                                goalWeight = v.d("goal"),
                                waist = v.optional("waist"),
                                activity = v.s("activity"),
                                averageSteps = v.i("steps"),
                                exercises = v.s("exercise"),
                                sleepHabits = v.s("sleep"),
                                sugaryDrinks = v.s("sugar"),
                                foodPreferences = v.s("foods"),
                                restrictions = v.s("restrictions"),
                                budget = v.d("budget"),
                                meals = v.s("meals"),
                                cooking = v.s("cooking"),
                                country = v.s("country"),
                                stepTarget = v.i("stepTarget"),
                                waterTarget = v.i("waterTarget"),
                                proteinTarget = v.d("proteinTarget"),
                                sleepTarget = v.d("sleepTarget"),
                            )
                        vm.repository.profile(profile, onboarding)
                    }
            },
        ) {
            Text(
                if (step < 2) "Continue →"
                else if (onboarding) "Start my journey" else "Save profile"
            )
        }
        if (step > 0) TextButton(onClick = { step-- }, enabled = !busy) { Text("Back") }
        if (!onboarding) TextButton(onClick = close, enabled = !busy) { Text("Cancel") }
    }
}

@Composable
fun WorkoutEditor(vm: MainViewModel, workout: Workout?, close: () -> Unit) {
    val w = remember { workout ?: Workout() }
    EditorDialog(
        "Activity session",
        listOf(
            Field("name", "Session name", w.name),
            Field("kind", "Type · Walking, Strength, or Other", w.kind),
            Field("date", "Date · YYYY-MM-DD", w.date),
            Field("minutes", "Duration · minutes", w.minutes.toString(), true),
        ),
        vm,
        close,
        save = {
            vm.repository.workout(
                w.copy(
                    name = it.s("name"),
                    kind = it.s("kind"),
                    date = it.s("date"),
                    minutes = it.i("minutes"),
                )
            )
        },
        extra = {
            Text(
                "Saving a session updates that day's walking or exercise total. Editing replaces its minutes; it won't count twice.",
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
}

@Composable
fun ComponentEditor(
    vm: MainViewModel,
    analysis: FoodAnalysis,
    index: Int,
    update: (FoodAnalysis) -> Unit,
    close: () -> Unit,
) {
    val c = analysis.components[index]
    val fields =
        listOf(
            Field("name", "Component name", c.name),
            Field("ingredients", "Ingredients · comma separated", c.ingredients.joinToString()),
            Field("serving", "Serving / portion description", c.serving),
            Field("gramsMin", "Estimated weight · lower g", c.gramsMin.toString(), true),
            Field("gramsMax", "Estimated weight · upper g", c.gramsMax.toString(), true),
            Field("calMin", "Estimated calories · lower kcal", c.caloriesMin.toString(), true),
            Field("calMax", "Estimated calories · upper kcal", c.caloriesMax.toString(), true),
            Field("protein", "Protein · g", c.protein.toString(), true),
            Field("carbs", "Carbohydrates · g", c.carbs.toString(), true),
            Field("fat", "Fat · g", c.fat.toString(), true),
            Field("fibre", "Fibre · g", c.fibre.toString(), true),
        )
    key(index) {
        EditorDialog(
            "Correct component estimates",
            fields,
            vm,
            close,
            save = { v ->
                val component =
                    c.copy(
                        name = v.s("name"),
                        ingredients =
                            v.s("ingredients")
                                .split(',')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() },
                        serving = v.s("serving"),
                        gramsMin = v.d("gramsMin"),
                        gramsMax = v.d("gramsMax"),
                        caloriesMin = v.d("calMin"),
                        caloriesMax = v.d("calMax"),
                        protein = v.d("protein"),
                        carbs = v.d("carbs"),
                        fat = v.d("fat"),
                        fibre = v.d("fibre"),
                    )
                val revised =
                    analysis.copy(
                        components =
                            analysis.components.mapIndexed { i, old ->
                                if (i == index) component else old
                            }
                    )
                revised.validate()
                update(revised)
            },
            extra = {
                Text(
                    "These remain estimates. If you change the amount, update nutrition too; ingredient composition and cooking oil can change the totals.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        )
    }
}
