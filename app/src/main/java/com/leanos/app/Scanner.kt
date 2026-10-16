package com.leanos.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.http.*

@Serializable
data class FoodComponent(
    val name: String,
    val ingredients: List<String>,
    val serving: String,
    val gramsMin: Double,
    val gramsMax: Double,
    val caloriesMin: Double,
    val caloriesMax: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fibre: Double,
)

@Serializable
data class FoodAnalysis(
    val name: String,
    val components: List<FoodComponent>,
    val cookingMethod: String,
    val sauces: List<String>,
    val confidence: String,
    val uncertainty: String,
    val source: String,
) {
    fun validate() {
        require(name.isNotBlank() && name.length <= 200 && components.size in 1..20) {
            "Incomplete food analysis. Try again or log manually."
        }
        require(
            confidence in listOf("low", "medium", "high") &&
                uncertainty.isNotBlank() &&
                source in listOf("image_estimate", "nutrition_label")
        ) {
            "Invalid analysis metadata."
        }
        components.forEach { c ->
            require(c.name.isNotBlank() && c.serving.isNotBlank() && c.ingredients.size <= 30)
            require(
                listOf(
                        c.gramsMin,
                        c.gramsMax,
                        c.caloriesMin,
                        c.caloriesMax,
                        c.protein,
                        c.carbs,
                        c.fat,
                        c.fibre,
                    )
                    .all { it.isFinite() && it in 0.0..20000.0 }
            ) {
                "Invalid nutrition in response."
            }
            require(c.gramsMin <= c.gramsMax && c.caloriesMin <= c.caloriesMax) {
                "Invalid estimate range."
            }
        }
    }

    fun meal() =
        Meal(
            name = name,
            calories = components.sumOf { (it.caloriesMin + it.caloriesMax) / 2 },
            protein = components.sumOf { it.protein },
            carbs = components.sumOf { it.carbs },
            fat = components.sumOf { it.fat },
            fibre = components.sumOf { it.fibre },
            notes =
                "AI estimate; ${components.sumOf { it.caloriesMin }.toInt()}–${components.sumOf { it.caloriesMax }.toInt()} kcal. $uncertainty\n" +
                    components.joinToString("\n") {
                        "${it.name}: ${it.serving}; ${it.gramsMin}–${it.gramsMax} g; ${it.ingredients.joinToString()}"
                    },
        )
}

interface ScannerApi {
    @Multipart
    @POST("analyze")
    suspend fun analyze(
        @Header("Authorization") auth: String,
        @Part image: MultipartBody.Part,
        @Part("mode") mode: okhttp3.RequestBody,
    ): okhttp3.ResponseBody
}

/** Device credential encrypted with a non-exportable Android Keystore key. Never backed up. */
@Singleton
class TokenVault @Inject constructor(app: Application) {
    private val prefs = app.getSharedPreferences("scanner_vault", 0)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("leanos_scanner", null) as? SecretKey)
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                                "leanos_scanner",
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build()
                    )
                }
                .generateKey()
    }

    fun save(token: String) {
        if (token.isBlank()) {
            prefs.edit().clear().apply()
            return
        }
        require(token.length in 32..256) {
            "Enter the deployment's client access token (32–256 characters)."
        }
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        prefs
            .edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(
                "data",
                Base64.encodeToString(cipher.doFinal(token.toByteArray()), Base64.NO_WRAP),
            )
            .apply()
    }

    fun read(): String {
        return try {
            val data = prefs.getString("data", null) ?: return ""
            val cipher =
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(
                        Cipher.DECRYPT_MODE,
                        key(),
                        GCMParameterSpec(
                            128,
                            Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP),
                        ),
                    )
                }
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)))
        } catch (_: Exception) {
            ""
        }
    }

    fun configured() = prefs.contains("data")
}

@Singleton
class FoodScanner @Inject constructor(private val app: Application, private val vault: TokenVault) {
    suspend fun analyze(uri: Uri, endpoint: String, label: Boolean): FoodAnalysis =
        withContext(Dispatchers.IO) {
            require(endpoint.startsWith("https://") && vault.configured()) {
                "Configure the scanner backend and client token in Profile first. Manual meals work offline."
            }
            val bitmap = decodePhoto(app, uri)
            val bytes =
                ByteArrayOutputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)
                    bitmap.recycle()
                    it.toByteArray()
                }
            require(bytes.size <= 5 * 1024 * 1024) { "Image too large. Crop and try again." }
            val client =
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(65, TimeUnit.SECONDS)
                    .callTimeout(75, TimeUnit.SECONDS)
                    .followRedirects(false)
                    .build()
            val api =
                Retrofit.Builder()
                    .baseUrl(endpoint.trimEnd('/') + "/")
                    .client(client)
                    .build()
                    .create(ScannerApi::class.java)
            val response =
                api.analyze(
                    "Bearer ${vault.read()}",
                    MultipartBody.Part.createFormData(
                        "image",
                        "food.jpg",
                        bytes.toRequestBody("image/jpeg".toMediaType()),
                    ),
                    (if (label) "label" else "food").toRequestBody("text/plain".toMediaType()),
                )
            val body = response.use { it.string() }
            require(body.length <= 100000) { "Unexpected scanner response size." }
            json.decodeFromString<FoodAnalysis>(body).also { it.validate() }
        }
}
