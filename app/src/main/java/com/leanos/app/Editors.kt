package com.leanos.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
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
    if (field.key == "photo") {
        MealPhotoPicker(values[field.key].orEmpty()) { values[field.key] = it }
        return
    }
    val options = choices(field, values)
    if (options.isNotEmpty()) {
        ChoiceControl(field, values, options)
        return
    }
    if (field.key == "exercise" && !field.numeric) {
        ExercisePreferences(field, values)
        return
    }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val isDate = field.key == "date"
    val isTime = field.key == "time"
    fun pickTime() {
        val t =
            runCatching { java.time.LocalTime.parse(values[field.key]) }
                .getOrDefault(java.time.LocalTime.now())
        android.app
            .TimePickerDialog(
                context,
                { _, h, m -> values[field.key] = java.time.LocalTime.of(h, m).toString() },
                t.hour,
                t.minute,
                true,
            )
            .show()
    }
    fun pickDate() {
        val selected =
            runCatching { java.time.LocalDate.parse(values[field.key]) }
                .getOrDefault(java.time.LocalDate.now())
        android.app
            .DatePickerDialog(
                context,
                { _, year, month, day ->
                    values[field.key] = java.time.LocalDate.of(year, month + 1, day).toString()
                },
                selected.year,
                selected.monthValue - 1,
                selected.dayOfMonth,
            )
            .apply { datePicker.maxDate = System.currentTimeMillis() }
            .show()
    }
    OutlinedTextField(
        value = values[field.key] ?: "",
        onValueChange = { if (it.length <= 10000) values[field.key] = it },
        label = { Text(field.label) },
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (field.numeric) KeyboardType.Decimal else KeyboardType.Text,
                imeAction = if (field.key == "notes") ImeAction.Default else ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        readOnly = isDate || isTime,
        trailingIcon =
            if (isDate)
                ({
                    IconButton(onClick = { pickDate() }) {
                        Icon(Icons.Outlined.CalendarMonth, "Choose date")
                    }
                })
            else if (isTime)
                ({
                    IconButton(onClick = { pickTime() }) {
                        Icon(Icons.Outlined.Schedule, "Choose meal time")
                    }
                })
            else null,
        singleLine = field.numeric || isDate || isTime,
        minLines = if (field.key == "notes") 3 else 1,
    )
    if (field.numeric) stepSize(field.key)?.let { NumberButtons(field, values, it) }
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
    var inlineError by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    var nutritionExpanded by rememberSaveable {
        mutableStateOf(
            fields.any {
                it.key in listOf("calories", "protein", "carbs", "fat", "fibre") &&
                    it.initial.isNotBlank()
            }
        )
    }
    fun requestClose() {
        if (!busy) {
            if (fields.any { values[it.key] != it.initial }) discard = true else onDismiss()
        }
    }
    Dialog(
        onDismissRequest = { requestClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Label("MAKE IT COUNT")
                        Text(title, style = MaterialTheme.typography.headlineMedium)
                    }
                    IconButton(onClick = { requestClose() }, enabled = !busy) {
                        Icon(Icons.Outlined.Close, "Close editor")
                    }
                }
                Text(
                    "Only saved entries update your progress.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val nutrients = setOf("calories", "protein", "carbs", "fat", "fibre")
                fields
                    .filter {
                        it.key !in nutrients &&
                            it.key != "notes" &&
                            (it.key !in setOf("sets", "reps", "loadKg") ||
                                values["kind"] == "Strength")
                    }
                    .forEach { field -> Input(field, values) }

                if (fields.any { it.key in nutrients }) {
                    TextButton(
                        onClick = { nutritionExpanded = !nutritionExpanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (nutritionExpanded) "− Hide optional nutrition"
                            else "+ Add nutrition (optional)"
                        )
                    }
                    if (nutritionExpanded)
                        fields.filter { it.key in nutrients }.forEach { Input(it, values) }
                }
                fields.filter { it.key == "notes" }.forEach { Input(it, values) }
                extra()
                inlineError?.let { Notice("Check this entry", it) }
                Button(
                    onClick = {
                        inlineError = null
                        vm.run(done = onSaved) {
                            try {
                                save(values.toMap())
                            } catch (e: Exception) {
                                inlineError = e.message ?: "Check your entry and try again."
                                throw e
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save record")
                }
                TextButton(
                    onClick = { requestClose() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text("Discard unsaved changes?") },
            text = { Text("This entry hasn't been saved yet.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        discard = false
                        onDismiss()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } },
        )
}

@Composable
fun MealEditor(
    vm: MainViewModel,
    meal: Meal?,
    photoSource: String? = null,
    onSaved: (() -> Unit)? = null,
    close: () -> Unit,
) {
    val m = remember { meal ?: Meal(name = "") }
    val fields =
        listOf(
            Field("name", "Food name", m.name),
            Field("photo", "Meal photo", photoSource ?: m.photo),
            Field("category", "Category (Brunch, Dinner, Snack…)", m.category),
            Field("date", "Date · YYYY-MM-DD", m.date),
            Field("time", "Time · HH:mm", m.time),
            Field("cost", "Total meal cost · RM (0 = free)", m.cost.toString(), true),
            Field("servings", "Servings", m.servings.toString(), true),
            Field(
                "calories",
                "Calories per serving · kcal (optional)",
                m.calories?.div(m.servings)?.toString() ?: "",
                true,
            ),
            Field(
                "protein",
                "Protein · g (optional)",
                m.protein?.div(m.servings)?.toString() ?: "",
                true,
            ),
            Field(
                "carbs",
                "Carbohydrates · g (optional)",
                m.carbs?.div(m.servings)?.toString() ?: "",
                true,
            ),
            Field("fat", "Fat · g (optional)", m.fat?.div(m.servings)?.toString() ?: "", true),
            Field(
                "fibre",
                "Fibre · g (optional)",
                m.fibre?.div(m.servings)?.toString() ?: "",
                true,
            ),
            Field("notes", "Ingredients, portions & notes", m.notes),
        )
    EditorDialog(
        if (meal == null) "Log a meal" else "Review & edit meal",
        fields,
        vm,
        close,
        save = { v ->
            vm.saveMeal(
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
                        servings = 1.0,
                    )
                    .withServings(v.d("servings")),
                v.s("photo"),
            )
        },
        onSaved = onSaved ?: close,
        extra = {
            Text(
                "Nutrition inputs describe one serving and scale to your serving quantity. Cost is for the entire meal. Nutrition is optional; photo estimates still need review.",
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
                Field(
                    "calorieTarget",
                    "Daily calorie target · kcal (optional)",
                    p.calorieTarget?.toString() ?: "",
                    true,
                ),
                Field(
                    "carbTarget",
                    "Carbohydrate target · g (optional)",
                    p.carbTarget?.toString() ?: "",
                    true,
                ),
                Field("fatTarget", "Fat target · g (optional)", p.fatTarget?.toString() ?: "", true),
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("About you", "Daily rhythm", "Food & budget").forEachIndexed { index, label ->
                FilterChip(
                    selected = step == index,
                    onClick = { step = index },
                    label = { Text("${index+1}", style = MaterialTheme.typography.labelLarge) },
                    leadingIcon =
                        if (index < step)
                            ({ Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) })
                        else null,
                )
            }
        }
        Panel { groups[step].forEach { Input(it, values) } }
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
                                calorieTarget = v.optional("calorieTarget"),
                                carbTarget = v.optional("carbTarget"),
                                fatTarget = v.optional("fatTarget"),
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
            Field("kind", "Activity type", w.kind),
            Field("workoutName", "Exercise", w.name),
            Field("date", "Date · YYYY-MM-DD", w.date),
            Field("minutes", "Duration · minutes", w.minutes.toString(), true),
            Field("sets", "Sets (optional)", w.sets.toString(), true),
            Field("reps", "Reps per set (optional)", w.reps.toString(), true),
            Field("loadKg", "Load · kg (optional)", w.loadKg?.toString() ?: "", true),
        ),
        vm,
        close,
        save = {
            vm.repository.workout(
                w.copy(
                    name = it.s("workoutName"),
                    kind = it.s("kind"),
                    date = it.s("date"),
                    minutes = it.i("minutes"),
                    sets = if (it.s("kind") == "Strength") it.i("sets") else 0,
                    reps = if (it.s("kind") == "Strength") it.i("reps") else 0,
                    loadKg = if (it.s("kind") == "Strength") it.optional("loadKg") else null,
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
