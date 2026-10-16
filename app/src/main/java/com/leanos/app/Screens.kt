package com.leanos.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@Composable
fun HomeScreen(data: Records, vm: MainViewModel, navigate: (String) -> Unit) {
    val p = data.profile!!
    val day = today()
    val h = data.habits.find { it.date == day }
    val meals = data.meals.filter { it.date == day }
    val nutrition = Calculations.nutrition(meals)
    val budget = Calculations.budget(p, data.meals, data.expenses)
    var addWeight by remember { mutableStateOf(false) }
    var addMeal by remember { mutableStateOf(false) }
    val current = data.currentWeight()
    val progress = Calculations.progress(p.startingWeight, current, p.goalWeight)
    val completion = h?.let { Calculations.completion(it, p, meals) } ?: 0f
    Page {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Label("LEANOS   /   ${LocalDate.now().dayOfWeek.name.lowercase()}")
                Text("Hey, ${p.name}.", style = MaterialTheme.typography.headlineLarge)
            }
            Icon(
                Icons.Outlined.Spa,
                "Your journey",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
        }
        Text("Small choices. Lasting change.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Panel {
            Label("YOUR JOURNEY")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${current.fmt()} kg",
                        fontSize = 38.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                    Text(
                        "${(current-p.startingWeight).fmt()} kg from start",
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        "Goal ${p.goalWeight.fmt()} kg",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Ring(progress, "${(progress*100).toInt()}%", "to your goal")
            }
            FilledTonalButton(onClick = { addWeight = true }, modifier = Modifier.fillMaxWidth()) {
                Text("+ Log a weigh-in")
            }
        }
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Daily rhythm", style = MaterialTheme.typography.titleLarge)
                Text("${(completion*100).toInt()}%", color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { completion }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Steps", "${h?.steps ?: 0} / ${p.stepTarget}")
                Metric("Water", "${h?.water ?: 0} ml")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Sugary drinks", h?.sugary?.toString() ?: "Not logged")
                Metric("Habit streak", "${Calculations.streak(data.habits,p,data.meals)} days")
            }
            TextButton(onClick = { navigate("track") }) { Text("Track your day →") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { navigate("scanner") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.DocumentScanner, null)
                Spacer(Modifier.width(6.dp))
                Text("Scan food")
            }
            OutlinedButton(onClick = { addMeal = true }, modifier = Modifier.weight(1f)) {
                Text("+ Add meal")
            }
        }
        Panel {
            val challengeDay =
                (ChronoUnit.DAYS.between(LocalDate.parse(p.startDate), LocalDate.now()) + 1)
                    .coerceAtLeast(1)
            Label("30-DAY HABIT CHALLENGE")
            Text(
                if (challengeDay <= 30) "Day $challengeDay · Build your rhythm"
                else "Your first 30 days",
                style = MaterialTheme.typography.titleLarge,
            )
            val complete =
                data.habits.count {
                    LocalDate.parse(it.date) in
                        LocalDate.parse(p.startDate)..LocalDate.parse(p.startDate).plusDays(29) &&
                        Calculations.completion(
                            it,
                            p,
                            data.meals.filter { m -> m.date == it.date },
                        ) >= 1f
                }
            Text("$complete of 30 days with all daily habits complete")
            LinearProgressIndicator(
                progress = { complete / 30f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "A missed day is a chance to begin again.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Panel {
            Text("Today's plate", style = MaterialTheme.typography.titleLarge)
            if (meals.isEmpty()) Empty("No meals yet. Calories are always optional.")
            else meals.forEach { Text("${it.time}  ${it.name}  ·  ${money(it.cost)}") }
            Text("Known estimates: ${nutrition[0].fmt(0)} kcal · ${nutrition[1].fmt(0)} g protein")
            Text(
                "${meals.count { it.calories!=null }}/${meals.size} meals include calories; totals may be incomplete.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Panel(Modifier.clickable { navigate("budget") }) {
            Label("FOOD BUDGET")
            Text("${money(budget.remaining)} left", style = MaterialTheme.typography.headlineMedium)
            Text("${money(budget.dailyAllowance)} / day · ${budget.daysRemaining} days left")
            Text("View budget →", color = MaterialTheme.colorScheme.primary)
        }
        Panel {
            Text("Weight · last 7 days", style = MaterialTheme.typography.titleLarge)
            Trend(
                data.weights
                    .filter { it.date >= LocalDate.now().minusDays(6).toString() }
                    .sortedBy { it.date }
                    .map { it.date to it.kg },
                "kg",
            )
        }
    }
    if (addWeight) WeightEditor(vm, null) { addWeight = false }
    if (addMeal) MealEditor(vm, null) { addMeal = false }
}

@Composable
fun TrackScreen(data: Records, vm: MainViewModel) {
    var date by rememberSaveable { mutableStateOf(today()) }
    var selected by rememberSaveable { mutableStateOf(today()) }
    val p = data.profile!!
    var sessionEditor by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<Workout?>(null) }
    var deleteSession by remember { mutableStateOf<Workout?>(null) }
    Page {
        Heading("Your daily rhythm", "Consistency over perfection.")
        OutlinedTextField(
            date,
            { date = it },
            label = { Text("Date · YYYY-MM-DD") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedButton(
            onClick = {
                vm.run(null) {
                    require(!LocalDate.parse(date).isAfter(LocalDate.now())) {
                        "Choose today or an earlier date."
                    }
                    selected = date
                }
            }
        ) {
            Text("Load day")
        }
        key(selected, data.habits.find { it.date == selected }) {
            val h = data.habits.find { it.date == selected } ?: Habit(date = selected)
            val fields =
                listOf(
                    Field("steps", "Steps", h.steps.toString(), true),
                    Field("walking", "Walking · minutes", h.walking.toString(), true),
                    Field(
                        "exercise",
                        "Strength / other exercise · minutes",
                        h.exercise.toString(),
                        true,
                    ),
                    Field("water", "Water · ml", h.water.toString(), true),
                    Field("sugary", "Sugary drinks · servings", h.sugary.toString(), true),
                    Field("sleep", "Sleep · hours", h.sleep.toString(), true),
                    Field("energy", "Energy · 1 (low) to 5 (high)", h.energy.toString(), true),
                    Field("notes", "Daily notes", h.notes),
                )
            val values = fieldValues(fields)
            var rest by rememberSaveable { mutableStateOf(h.rest) }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(
                        Calculations.completion(h, p, data.meals.filter { it.date == selected }),
                        "${(Calculations.completion(h,p,data.meals.filter { it.date==selected })*100).toInt()}%",
                        "saved habits",
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(selected)
                        Text("${data.meals.count { it.date==selected }} meals logged")
                        Text(
                            "${data.meals.filter { it.date==selected }.sumOf { it.protein ?: 0.0 }.fmt(0)} / ${p.proteinTarget.fmt(0)} g protein"
                        )
                    }
                }
                Text(
                    "Meals and protein are calculated from your meal records. Unknown protein is not counted.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            fields.forEach { Input(it, values) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(rest, { rest = it })
                Spacer(Modifier.width(12.dp))
                Text("Rest day")
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    vm.run {
                        vm.repository.habit(
                            Habit(
                                date = selected,
                                steps = values.i("steps"),
                                walking = values.i("walking"),
                                exercise = values.i("exercise"),
                                water = values.i("water"),
                                sugary = values.i("sugary"),
                                sleep = values.d("sleep"),
                                energy = values.i("energy"),
                                notes = values.s("notes"),
                                rest = rest,
                            )
                        )
                    }
                },
            ) {
                Text("Save day · partial entries welcome")
            }
        }
        Panel {
            Text("This week", style = MaterialTheme.typography.titleLarge)
            val days = data.habits.filter { it.date >= LocalDate.now().minusDays(6).toString() }
            Text(
                "${days.sumOf { it.steps }} steps · ${days.sumOf { it.walking+it.exercise }} active minutes"
            )
            Text(
                "${days.count { it.walking>0 || it.exercise>0 }} active days · ${days.count { it.rest }} rest days"
            )
            Text(
                "${days.size} days recorded · ${Calculations.streak(data.habits,p,data.meals)}-day complete habit streak"
            )
        }
        Button(
            onClick = {
                session = null
                sessionEditor = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("+ Walking / workout session")
        }
        data.workouts
            .filter { it.date == selected }
            .forEach { w ->
                Panel {
                    Text(
                        "${w.name} · ${w.minutes} min",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(w.kind)
                    Row {
                        TextButton(
                            onClick = {
                                session = w
                                sessionEditor = true
                            }
                        ) {
                            Text("Edit session")
                        }
                        TextButton(onClick = { deleteSession = w }) { Text("Delete") }
                    }
                }
            }
        Panel {
            Text("Beginner strength", style = MaterialTheme.typography.titleLarge)
            Text(
                "Try 1–2 comfortable rounds: 5 chair squats, 5 wall push-ups, 5 supported hip hinges. Rest 60–90 seconds between movements. Log the minutes above."
            )
            Text(
                "Walk at a comfortable pace. Increase gradually; rest when needed. Stop if you feel pain or dizziness.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (data.habits.isNotEmpty()) {
            Label("PAST ENTRIES")
            data.habits.take(60).forEach { h ->
                TextButton(
                    onClick = {
                        date = h.date
                        selected = h.date
                    }
                ) {
                    Text("${h.date} · ${h.steps} steps · ${h.sleep.fmt()} h sleep")
                }
            }
        }
    }
    if (sessionEditor)
        WorkoutEditor(vm, session ?: Workout(date = selected)) { sessionEditor = false }
    deleteSession?.let { w ->
        Confirm(
            "Delete session?",
            "The session's minutes will be removed from daily totals.",
            { deleteSession = null },
        ) {
            vm.run(done = { deleteSession = null }) { vm.repository.delete(w) }
        }
    }
}

@Composable
fun MealsScreen(data: Records, vm: MainViewModel) {
    var editor by remember { mutableStateOf(false) }
    var meal by remember { mutableStateOf<Meal?>(null) }
    var delete by remember { mutableStateOf<Meal?>(null) }
    val week = data.meals.filter { it.date >= LocalDate.now().minusDays(6).toString() }
    val n = Calculations.nutrition(week)
    Page {
        Heading("Good food. Less friction.", "Brunch, dinner, and everything between.")
        Button(
            onClick = {
                meal = null
                editor = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("+ Log meal")
        }
        Panel {
            Label("LAST 7 DAYS · KNOWN NUTRITION")
            Text(
                "${n[0].fmt(0)} kcal  ·  ${n[1].fmt(0)} g protein",
                style = MaterialTheme.typography.titleLarge,
            )
            Text("Carbs ${n[2].fmt(0)} g · Fat ${n[3].fmt(0)} g · Fibre ${n[4].fmt(0)} g")
            Text(
                "${week.size} meals · ${money(week.sumOf { it.cost })} · ${week.count { it.calories!=null }} with calorie estimates",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Ideas: nasi ayam, nasi bujang, nasi campur, roti canai, bihun sup, eggs, tofu, chicken, fish. Log what you actually eat; no calorie target required.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (data.meals.isEmpty()) Empty("Your meals will appear here after you log them.")
        data.meals.forEach { m ->
            Panel {
                Label("${m.date} · ${m.time} · ${m.category}")
                Text(m.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${money(m.cost)} · ${m.calories?.let { "${it.fmt(0)} kcal estimated" } ?: "Nutrition not entered"}"
                )
                if (m.notes.isNotBlank())
                    Text(
                        m.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                Row {
                    TextButton(
                        onClick = {
                            meal = m
                            editor = true
                        }
                    ) {
                        Text("Edit")
                    }
                    TextButton(onClick = { delete = m }) { Text("Delete") }
                }
            }
        }
    }
    if (editor) MealEditor(vm, meal) { editor = false }
    delete?.let { m ->
        Confirm(
            "Delete ${m.name}?",
            "Its food cost will also be removed from your budget.",
            { delete = null },
        ) {
            vm.run(done = { delete = null }) { vm.repository.delete(m) }
        }
    }
}

@Composable
fun ProgressScreen(data: Records, vm: MainViewModel) {
    var editor by remember { mutableStateOf(false) }
    var weight by remember { mutableStateOf<Weight?>(null) }
    var delete by remember { mutableStateOf<Weight?>(null) }
    var range by rememberSaveable { mutableIntStateOf(30) }
    val p = data.profile!!
    Page {
        Heading("The bigger picture", "Progress is a trend, never a deadline.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("Current", "${data.currentWeight().fmt()} kg", Modifier.weight(1f))
            Metric(
                "Change",
                "${(data.currentWeight()-p.startingWeight).fmt()} kg",
                Modifier.weight(1f),
            )
        }
        LinearProgressIndicator(
            progress = {
                Calculations.progress(p.startingWeight, data.currentWeight(), p.goalWeight)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                weight = null
                editor = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("+ Weight & waist")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(7, 30, 90, 36500).forEach { days ->
                FilterChip(
                    selected = range == days,
                    onClick = { range = days },
                    label = { Text(if (days == 36500) "All" else "${days}d") },
                )
            }
        }
        val selected =
            data.weights
                .filter { it.date >= LocalDate.now().minusDays((range - 1).toLong()).toString() }
                .sortedBy { it.date }
        Panel {
            Text("Weight trend", style = MaterialTheme.typography.titleLarge)
            Trend(selected.map { it.date to it.kg }, "kg")
        }
        Panel {
            Text("Waist trend", style = MaterialTheme.typography.titleLarge)
            Trend(selected.mapNotNull { w -> w.waist?.let { w.date to it } }, "cm")
        }
        Panel {
            Text("Monthly summary", style = MaterialTheme.typography.titleLarge)
            val month =
                data.weights.filter { it.date.startsWith(today().take(7)) }.sortedBy { it.date }
            if (month.isEmpty()) Empty("No weigh-ins this month.")
            else {
                Text("${month.size} weigh-ins · Average ${month.map { it.kg }.average().fmt()} kg")
                if (month.size > 1)
                    Text(
                        "${(month.last().kg-month.first().kg).fmt()} kg between first and last recorded weigh-in"
                    )
            }
        }
        Label("WEIGH-IN HISTORY")
        data.weights.forEach { w ->
            Panel {
                Text("${w.date}  ·  ${w.kg.fmt()} kg", style = MaterialTheme.typography.titleMedium)
                w.waist?.let { Text("Waist ${it.fmt()} cm") }
                Row {
                    TextButton(
                        onClick = {
                            weight = w
                            editor = true
                        }
                    ) {
                        Text("Edit")
                    }
                    TextButton(onClick = { delete = w }) { Text("Delete") }
                }
            }
        }
    }
    if (editor) WeightEditor(vm, weight) { editor = false }
    delete?.let { w ->
        Confirm("Delete weigh-in?", "Remove the measurement for ${w.date}?", { delete = null }) {
            vm.run(done = { delete = null }) { vm.repository.delete(w) }
        }
    }
}

@Composable
fun BudgetScreen(data: Records, vm: MainViewModel, back: () -> Unit) {
    val p = data.profile!!
    val b = Calculations.budget(p, data.meals, data.expenses)
    var editor by remember { mutableStateOf(false) }
    var expense by remember { mutableStateOf<Expense?>(null) }
    var delete by remember { mutableStateOf<Expense?>(null) }
    Page {
        TextButton(onClick = back) { Text("← Home") }
        Heading("Make every ringgit count.", "30-day food budget · ${b.start} to ${b.end}")
        Panel {
            Label("REMAINING")
            Text(money(b.remaining), style = MaterialTheme.typography.displaySmall)
            LinearProgressIndicator(
                progress = {
                    if (p.budget == 0.0) 0f else (b.spent / p.budget).toFloat().coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${money(b.spent)} spent of ${money(p.budget)}")
            Text("${money(b.dailyAllowance)} available / day · ${b.daysRemaining} days left")
            Text("Average daily spend: ${money(b.spent/(31-b.daysRemaining))}")
        }
        if (b.remaining < 0 || b.dailyAllowance < 5)
            Panel {
                Text(
                    if (b.remaining < 0) "You've exceeded this period's budget."
                    else "Your food budget is running low.",
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    "Review expenses or adjust your budget in Profile. Keep regular meals; never skip meals to save money."
                )
            }
        Button(
            onClick = {
                expense = null
                editor = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("+ Other food expense")
        }
        Text(
            "Logged meals are counted automatically, including free meals at RM0. Use other expenses only for purchases not already logged as meals.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val daily =
            (data.meals
                    .filter { LocalDate.parse(it.date) in b.start..b.end }
                    .map { it.date to it.cost } +
                    data.expenses
                        .filter { LocalDate.parse(it.date) in b.start..b.end }
                        .map { it.date to it.cost })
                .groupBy({ it.first }, { it.second })
                .toSortedMap()
        Panel {
            Text("Daily spending", style = MaterialTheme.typography.titleLarge)
            Trend(daily.map { it.key to it.value.sum() }, "RM")
        }
        Label("OTHER EXPENSES")
        if (data.expenses.isEmpty()) Empty("No other food expenses.")
        data.expenses.forEach { e ->
            Panel {
                Text("${e.title} · ${money(e.cost)}", style = MaterialTheme.typography.titleMedium)
                Text(e.date)
                Row {
                    TextButton(
                        onClick = {
                            expense = e
                            editor = true
                        }
                    ) {
                        Text("Edit")
                    }
                    TextButton(onClick = { delete = e }) { Text("Delete") }
                }
            }
        }
    }
    if (editor) ExpenseEditor(vm, expense) { editor = false }
    delete?.let { e ->
        Confirm("Delete expense?", e.title, { delete = null }) {
            vm.run(done = { delete = null }) { vm.repository.delete(e) }
        }
    }
}

@Composable
fun Confirm(title: String, text: String, cancel: () -> Unit, confirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = cancel,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = confirm) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } },
    )
}
