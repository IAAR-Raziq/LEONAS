package com.leanos.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel
@Inject
constructor(
    val repository: Repository,
    val preferences: Preferences,
    private val scanner: FoodScanner,
    val vault: TokenVault,
    private val app: Application,
    val photos: PhotoStore,
) : ViewModel() {
    val loadError = MutableStateFlow<String?>(null)
    private val reload = MutableStateFlow(0)

    fun reload() {
        reload.value++
    }

    val records =
        reload
            .flatMapLatest {
                repository.records
                    .onStart { loadError.value = null }
                    .catch {
                        loadError.value =
                            "Unable to open your records. Your stored data has not been reset. Retry or reopen the app."
                    }
            }
            .map<Records, Records?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val favorites =
        preferences.favorites.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptySet(),
        )
    val theme =
        preferences.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Dark")
    val endpoint =
        preferences.endpoint.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val analysis = MutableStateFlow<FoodAnalysis?>(null)

    private fun error(e: Exception): String =
        when (e) {
            is retrofit2.HttpException ->
                when (e.code()) {
                    401,
                    403 -> "Scanner access denied. Check the client token in Profile."
                    429 -> "Scanner quota reached. Try later or log manually."
                    422 ->
                        "Food could not be analyzed reliably. Try a clearer photo or log manually."
                    else -> "Scanner unavailable (${e.code()}). Try later or log manually."
                }
            is java.io.IOException ->
                "Connection or storage unavailable. Check connectivity and try again."
            else -> e.message ?: "Unable to complete this action. Please try again."
        }

    fun run(success: String? = "Saved", done: () -> Unit = {}, action: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                action()
                message.value = success
                done()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = error(e)
            } finally {
                busy.value = false
            }
        }
    }

    fun scan(uri: Uri, label: Boolean) {
        analysis.value = null
        run(null) { analysis.value = scanner.analyze(uri, endpoint.value, label) }
    }

    suspend fun erasePersonalData() {
        repository.erase()
        preferences.reset()
        vault.save("")
        analysis.value = null
        photos.erase()
        withContext(Dispatchers.IO) {
            app.cacheDir.deleteRecursively()
            app.cacheDir.mkdirs()
        }
    }

    suspend fun saveMeal(meal: Meal, selection: String) {
        // Validate the form before copying a photo or changing any records.
        meal.copy(photo = "").validate()
        val photo = photos.save(selection)
        try {
            repository.meal(meal.copy(photo = photo))
        } finally {
            photos.cleanup(repository.records.first().meals.map { it.photo }.toSet())
        }
    }

    suspend fun deleteMeal(meal: Meal) {
        repository.delete(meal)
        photos.cleanup(repository.records.first().meals.map { it.photo }.toSet())
    }

    fun export(uri: Uri) =
        run("Backup exported, including meal photos. Keep it private.") {
            val current = repository.records.first()
            val backup =
                current
                    .backup()
                    .copy(
                        favoriteMealIds =
                            preferences.favorites
                                .first()
                                .intersect(current.meals.map { it.id }.toSet())
                    )
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(uri, "wt")?.use {
                    photos.writeBackup(it, backup)
                } ?: error("Cannot write backup.")
            }
        }

    fun import(uri: Uri) =
        run("Backup restored, including available meal photos") {
            val backup =
                withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { photos.readBackup(it) }
                        ?: error("Cannot read backup.")
                }
            try {
                repository.restore(backup)
                preferences.restoreFavorites(backup.favoriteMealIds)
            } finally {
                photos.cleanup(repository.records.first().meals.map { it.photo }.toSet())
            }
        }
}
