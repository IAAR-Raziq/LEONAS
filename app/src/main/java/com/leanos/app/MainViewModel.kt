package com.leanos.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

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
        withContext(Dispatchers.IO) {
            app.cacheDir.deleteRecursively()
            app.cacheDir.mkdirs()
        }
    }

    fun export(uri: Uri) =
        run("Backup exported. Keep this personal health data private.") {
            val backup = repository.records.first().backup()
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(json.encodeToString(backup).toByteArray())
                } ?: error("Cannot write to this document.")
            }
        }

    fun import(uri: Uri) =
        run("Backup restored") {
            val text =
                withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use {
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var count = it.read(buffer)
                        while (count != -1) {
                            require(output.size() + count <= 10 * 1024 * 1024) {
                                "Backup exceeds 10 MB."
                            }
                            output.write(buffer, 0, count)
                            count = it.read(buffer)
                        }
                        output.toString("UTF-8")
                    } ?: error("Cannot read backup.")
                }
            repository.restore(json.decodeFromString<Backup>(text))
        }
}
