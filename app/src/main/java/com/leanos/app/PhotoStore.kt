package com.leanos.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.*
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

fun isPhotoName(name: String) = Regex("[0-9a-f-]{36}\\.jpg").matches(name)

@Singleton
class PhotoStore @Inject constructor(private val app: Application) {
    private val directory
        get() = File(app.filesDir, "meal_photos").apply { mkdirs() }

    fun file(name: String): File {
        require(isPhotoName(name))
        return File(directory, name)
    }

    fun uri(name: String): Uri = Uri.fromFile(file(name))

    suspend fun save(source: String): String =
        withContext(Dispatchers.IO) {
            if (source.isBlank()) return@withContext ""
            if (isPhotoName(source)) {
                require(file(source).isFile) {
                    "Photo is missing. Choose another photo or remove it."
                }
                return@withContext source
            }
            val uri = Uri.parse(source)
            require(uri.scheme in listOf("content", "file")) { "Choose a camera or gallery photo." }
            val bitmap = decodePhoto(app, uri, 1280)
            val name = "${UUID.randomUUID()}.jpg"
            try {
                file(name).outputStream().use {
                    require(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)) {
                        "Unable to save photo."
                    }
                }
                require(file(name).length() <= 1024 * 1024) {
                    "Photo too large. Choose a smaller image."
                }
                name
            } catch (e: Exception) {
                file(name).delete()
                throw e
            } finally {
                bitmap.recycle()
            }
        }

    suspend fun cleanup(used: Set<String>) =
        withContext(Dispatchers.IO) {
            directory
                .listFiles()
                ?.filter { isPhotoName(it.name) && it.name !in used }
                ?.forEach { it.delete() }
            Unit
        }

    suspend fun erase() =
        withContext(Dispatchers.IO) {
            directory.deleteRecursively()
            Unit
        }

    suspend fun writeBackup(output: OutputStream, backup: Backup) =
        withContext(Dispatchers.IO) {
            backup.validate()
            val metadata = json.encodeToString(backup).toByteArray()
            require(metadata.size <= 10 * 1024 * 1024) { "Record metadata exceeds 10 MB." }
            val names = backup.meals.map { it.photo }.filter { it.isNotEmpty() }.toSet()
            require(names.size <= 5000 && names.sumOf { file(it).length() } <= 100L * 1024 * 1024) {
                "Photo backup exceeds 100 MB."
            }
            require(names.all { file(it).isFile }) {
                "A meal photo is missing. Remove or replace it before exporting."
            }
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("records.json"))
                zip.write(metadata)
                zip.closeEntry()
                names.forEach { name ->
                    zip.putNextEntry(ZipEntry("photos/$name"))
                    file(name).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }

    private fun readBounded(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var count = input.read(buffer)
        while (count != -1) {
            require(out.size() + count <= max) { "Backup entry too large." }
            out.write(buffer, 0, count)
            count = input.read(buffer)
        }
        return out.toByteArray()
    }

    /**
     * All records and images are validated before the database is touched. New UUIDs prevent
     * overwriting existing photos.
     */
    suspend fun readBackup(input: InputStream): Backup =
        withContext(Dispatchers.IO) {
            val stream = BufferedInputStream(input)
            stream.mark(4)
            val first = stream.read()
            val second = stream.read()
            stream.reset()
            if (first != 80 || second != 75) {
                val backup =
                    json.decodeFromString<Backup>(
                        readBounded(stream, 10 * 1024 * 1024).toString(Charsets.UTF_8)
                    )
                backup.validate()
                require(backup.meals.none { it.photo.isNotEmpty() }) {
                    "Photo backups must use the LEANOS ZIP format."
                }
                return@withContext backup
            }
            val created = mutableListOf<String>()
            val mapping = mutableMapOf<String, String>()
            var total = 0L
            try {
                ZipInputStream(stream).use { zip ->
                    val entry = zip.nextEntry
                    require(entry?.name == "records.json") { "Not a LEANOS backup." }
                    val backup =
                        json.decodeFromString<Backup>(
                            readBounded(zip, 10 * 1024 * 1024).toString(Charsets.UTF_8)
                        )
                    backup.validate()
                    zip.closeEntry()
                    val expected = backup.meals.map { it.photo }.filter { it.isNotEmpty() }.toSet()
                    require(expected.size <= 5000)
                    var photoEntry = zip.nextEntry
                    while (photoEntry != null) {
                        val original = photoEntry.name.removePrefix("photos/")
                        require(
                            photoEntry.name == "photos/$original" &&
                                original in expected &&
                                original !in mapping &&
                                isPhotoName(original)
                        ) {
                            "Unexpected or duplicate photo entry."
                        }
                        val bytes = readBounded(zip, 1024 * 1024)
                        total += bytes.size
                        require(total <= 100L * 1024 * 1024) { "Photo backup exceeds 100 MB." }
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        require(
                            bounds.outWidth in 1..1600 &&
                                bounds.outHeight in 1..1600 &&
                                bounds.outMimeType == "image/jpeg"
                        ) {
                            "Invalid backup photo."
                        }
                        val bitmap =
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                ?: error("Invalid backup photo.")
                        val name = "${UUID.randomUUID()}.jpg"
                        created += name
                        try {
                            file(name).outputStream().use {
                                require(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it))
                            }
                        } finally {
                            bitmap.recycle()
                        }
                        require(file(name).length() <= 1024 * 1024) {
                            "Backup photo exceeds the supported image size."
                        }
                        mapping[original] = name
                        zip.closeEntry()
                        photoEntry = zip.nextEntry
                    }
                    require(mapping.keys == expected) { "Backup is missing meal photos." }
                    backup.copy(
                        meals =
                            backup.meals.map { m ->
                                m.copy(
                                    photo = if (m.photo.isEmpty()) "" else mapping.getValue(m.photo)
                                )
                            }
                    )
                }
            } catch (e: Exception) {
                created.forEach { file(it).delete() }
                throw e
            }
        }
}
