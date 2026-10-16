package com.leanos.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    var addActivity by remember { mutableStateOf(false) }
    val current = data.currentWeight()
    val progress = Calculations.progress(p.startingWeight, current, p.goalWeight)
    val completion = h?.let { Calculations.completion(it, p, meals) } ?: 0f
    Page {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(
                    LocalDate.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMM"))
                )
                Text("Hey, ${p.name}.", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Let's make today a little better.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(
                onClick = { navigate("profile") },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Outlined.Person, "Open your profile")
            }
        }
        HeroPanel {
            Label("YOUR JOURNEY")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        current.fmt(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                    Text(
                        "kilograms · current weight",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(current-p.startingWeight).fmt()} kg since you began",
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Ring(progress, "${(progress*100).toInt()}%", "to ${p.goalWeight.fmt(0)} kg")
            }
            FilledTonalButton(onClick = { addWeight = true }, modifier = Modifier.fillMaxWidth()) {
                Text("+ Log a weigh-in")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { navigate("scanner") },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Scan food")
            }
            OutlinedButton(
                onClick = { addMeal = true },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Text("+ Add meal")
            }
        }
        FilledTonalButton(onClick = { addActivity = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Activity")
        }
        SectionTitle("Today, at a glance", "Small habits add up. Log what you can.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Steps",
                "${h?.steps ?: 0}",
                "of ${p.stepTarget} today",
                Icons.Outlined.DirectionsWalk,
                Modifier.weight(1f),
            )
            StatTile(
                "Water",
                "${h?.water ?: 0} ml",
                "of ${p.waterTarget} ml",
                Icons.Outlined.WaterDrop,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Habit streak",
                "${Calculations.streak(data.habits,p,data.meals)} days",
                "All seven habits complete",
                Icons.Outlined.LocalFireDepartment,
                Modifier.weight(1f),
            )
            StatTile(
                "Sugary drinks",
                h?.sugary?.toString() ?: "—",
                if (h == null) "Not logged yet" else "servings today",
                Icons.Outlined.LocalCafe,
                Modifier.weight(1f),
            )
        }
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Daily rhythm", style = MaterialTheme.typography.titleMedium)
                Text("${(completion*100).toInt()}%", color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { completion }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { navigate("track") }) { Text("Track your day →") }
        }
        Panel(Modifier.clickable { navigate("budget") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AccountBalanceWallet,
                    null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(10.dp))
                SectionTitle("Your food budget", "${budget.daysRemaining} days left this period")
            }
            Text(money(budget.remaining), style = MaterialTheme.typography.headlineLarge)
            Text(
                "${money(budget.dailyAllowance)} available per day",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("View budget →", color = MaterialTheme.colorScheme.primary)
        }
        Panel {
            SectionTitle("Today's plate", "Nutrition is optional. Your meals still count.")
            if (meals.isEmpty()) Empty("Nothing logged yet. Add your first meal when you're ready.")
            meals.forEach {
                MealPhoto(it.photo)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(it.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            it.time,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(money(it.cost))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("${nutrition[0].fmt(0)} kcal estimated · ${nutrition[1].fmt(0)} g known protein")
            Text(
                "${meals.count {it.calories!=null}} of ${meals.size} meals include calories. Totals may be incomplete.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Panel {
            SectionTitle("Your weekly trend", "Your saved weigh-ins from the last seven days")
            Trend(
                data.weights
                    .filter { it.date >= LocalDate.now().minusDays(6).toString() }
                    .sortedBy { it.date }
                    .map { it.date to it.kg },
                "kg",
            )
        }
        val challengeDay =
            (ChronoUnit.DAYS.between(LocalDate.parse(p.startDate), LocalDate.now()) + 1)
                .coerceAtLeast(1)
        val complete =
            data.habits.count {
                LocalDate.parse(it.date) in
                    LocalDate.parse(p.startDate)..LocalDate.parse(p.startDate).plusDays(29) &&
                    Calculations.completion(it, p, data.meals.filter { m -> m.date == it.date }) >=
                        1f
            }
        Panel {
            Label("30-DAY HABIT CHALLENGE")
            Text(
                if (challengeDay <= 30) "Day $challengeDay · Keep showing up"
                else "Your first 30 days",
                style = MaterialTheme.typography.titleLarge,
            )
            Text("$complete of 30 days complete", color = MaterialTheme.colorScheme.secondary)
            LinearProgressIndicator(
                progress = { complete / 30f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "A missed day never erases your progress.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (addWeight) WeightEditor(vm, null) { addWeight = false }
    if (addMeal) MealEditor(vm, null) { addMeal = false }
    if (addActivity) WorkoutEditor(vm, null) { addActivity = false }
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = {
                    selected = LocalDate.parse(selected).minusDays(1).toString()
                    date = selected
                }
            ) {
                Icon(Icons.Outlined.ChevronLeft, "Previous day")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (selected == today()) "Today"
                    else
                        LocalDate.parse(selected)
                            .format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    selected,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = {
                    selected = LocalDate.parse(selected).plusDays(1).toString()
                    date = selected
                },
                enabled = selected < today(),
            ) {
                Icon(Icons.Outlined.ChevronRight, "Next day")
            }
        }
        ChooseDay(selected) {
            selected = it
            date = it
        }
        Button(
            onClick = {
                session = null
                sessionEditor = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Activity")
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
            Panel {
                SectionTitle("Move your body", "Set a comfortable pace")
                fields
                    .filter { it.key in listOf("steps", "walking", "exercise") }
                    .forEach { Input(it, values) }
            }
            Panel {
                SectionTitle("Fuel & recovery", "Your daily essentials")
                fields
                    .filter { it.key in listOf("water", "sugary", "sleep") }
                    .forEach { Input(it, values) }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(250, 500).forEach { ml ->
                        AssistChip(
                            onClick = {
                                values["water"] =
                                    ((values["water"]?.toIntOrNull() ?: 0) + ml).toString()
                            },
                            label = { Text("+ $ml ml") },
                        )
                    }
                }
                Text(
                    "Water changes are saved with your day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Panel {
                SectionTitle("How did you feel?")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..5).forEach { level ->
                        FilterChip(
                            selected = values["energy"] == level.toString(),
                            onClick = { values["energy"] = level.toString() },
                            label = { Text(level.toString()) },
                        )
                    }
                }
                Text(
                    "1 = low energy · 5 = full of energy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Input(fields.first { it.key == "notes" }, values)
            }
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
        data.workouts
            .filter { it.date == selected }
            .forEach { w ->
                Panel {
                    Text(
                        "${w.name} · ${w.minutes} min",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(w.kind)
                    if (w.kind == "Strength" && (w.sets > 0 || w.reps > 0))
                        Text(
                            "${w.sets} sets × ${w.reps} reps${w.loadKg?.let { " · ${it.fmt()} kg" } ?: ""}"
                        )
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
    var search by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf("All") }
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf("All meals") }
    val profile = data.profile!!
    var diaryDay by rememberSaveable { mutableStateOf(today()) }
    val visible =
        data.meals.filter { m ->
            (search.isBlank() || m.name.contains(search, true) || m.notes.contains(search, true)) &&
                (category == "All meals" || m.category == category) &&
                (!onlyFavorites || m.id in favorites) &&
                when (period) {
                    "Today" -> m.date == today()
                    "7 days" -> m.date >= LocalDate.now().minusDays(6).toString()
                    else -> true
                }
        }
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
        HeroPanel {
            SectionTitle("Daily food diary", if (diaryDay == today()) "Today" else diaryDay)
            ChooseDay(diaryDay) { diaryDay = it }
            val logged = data.meals.filter { it.date == diaryDay }
            val totals = Calculations.nutrition(logged)
            Text("${totals[0].fmt(0)} kcal logged", style = MaterialTheme.typography.headlineMedium)
            profile.calorieTarget?.let { target ->
                Text("${target.fmt(0)} kcal target · ${(target-totals[0]).fmt(0)} kcal remaining")
                LinearProgressIndicator(
                    progress = { (totals[0] / target).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
                ?: Text(
                    "No calorie target set. Add one in Profile if you want.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            Text("Protein ${totals[1].fmt(0)} g / ${profile.proteinTarget.fmt(0)} g")
            Text(
                "Carbs ${totals[2].fmt(0)} g${profile.carbTarget?.let {" / ${it.fmt(0)} g"} ?: ""} · Fat ${totals[3].fmt(0)} g${profile.fatTarget?.let {" / ${it.fmt(0)} g"} ?: ""}"
            )
            Text("Fibre ${totals[4].fmt(0)} g")
            Text(
                "${logged.count {it.calories!=null}} of ${logged.size} meals include calories. Remaining values are estimates and may be incomplete; activity calories are not added to your food allowance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        SectionTitle("Meal journal", "Find something you've logged")
        OutlinedTextField(
            search,
            { search = it },
            label = { Text("Search meals") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today", "7 days", "All").forEach { p ->
                FilterChip(period == p, { period = p }, label = { Text(p) })
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("All meals", "Breakfast", "Brunch", "Lunch", "Dinner", "Snack", "Drink")
                .forEach { option ->
                    FilterChip(category == option, { category = option }, label = { Text(option) })
                }
        }
        FilterChip(
            onlyFavorites,
            { onlyFavorites = !onlyFavorites },
            label = { Text("Favourites") },
            leadingIcon = { Icon(Icons.Outlined.Star, null, Modifier.size(18.dp)) },
        )
        if (visible.isEmpty())
            Empty(
                if (data.meals.isEmpty())
                    "Your first meal starts your journal. No calorie counting needed."
                else "No meals match these filters."
            )
        visible.forEach { m ->
            Panel {
                Label("${m.date} · ${m.time} · ${m.category}")
                MealPhoto(m.photo)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        m.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.run(null) { vm.preferences.favorite(m.id) } }) {
                        Icon(
                            if (m.id in favorites) Icons.Outlined.Star
                            else Icons.Outlined.StarBorder,
                            if (m.id in favorites) "Remove favourite ${m.name}"
                            else "Favourite ${m.name}",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    "${m.servings.fmt()} serving${if(m.servings==1.0) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    TextButton(
                        onClick = {
                            meal =
                                m.copy(
                                    id = java.util.UUID.randomUUID().toString(),
                                    date = today(),
                                    time =
                                        java.time.LocalTime.now()
                                            .withSecond(0)
                                            .withNano(0)
                                            .toString(),
                                )
                            editor = true
                        }
                    ) {
                        Text("Log again")
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
            vm.run(done = { delete = null }) { vm.deleteMeal(m) }
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
        HeroPanel {
            Label("YOUR LONG-TERM VIEW")
            Text("${data.currentWeight().fmt()} kg", style = MaterialTheme.typography.displaySmall)
            Text(
                "${(data.currentWeight()-p.startingWeight).fmt()} kg from your starting weight",
                color = MaterialTheme.colorScheme.secondary,
            )
            LinearProgressIndicator(
                progress = {
                    Calculations.progress(p.startingWeight, data.currentWeight(), p.goalWeight)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Starting ${p.startingWeight.fmt()} kg  →  Goal ${p.goalWeight.fmt()} kg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        HeroPanel {
            Label("AVAILABLE THIS PERIOD")
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
