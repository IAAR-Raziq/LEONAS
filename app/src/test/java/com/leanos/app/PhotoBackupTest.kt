package com.leanos.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.*
import java.util.zip.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoBackupTest {
    private fun backup(meal: Meal) =
        Backup(
            profile = Profile(name = "Test"),
            habits = emptyList(),
            meals = listOf(meal),
            weights = emptyList(),
            expenses = emptyList(),
        )

    @Test
    fun photoPersistsAndZipBackupRestoresWithNewNames() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = PhotoStore(app)
        val source = File(app.cacheDir, "source.jpg")
        val bitmap =
            Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.GREEN)
            }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        bitmap.recycle()
        val photo = store.save(Uri.fromFile(source).toString())
        assertTrue(store.file(photo).isFile)
        val meal = Meal(name = "Nasi ayam", photo = photo)
        val out = ByteArrayOutputStream()
        store.writeBackup(out, backup(meal).copy(favoriteMealIds = setOf(meal.id)))
        val restored = store.readBackup(ByteArrayInputStream(out.toByteArray()))
        val next = restored.meals.single().photo
        assertNotEquals(photo, next)
        assertTrue(store.file(next).isFile)
        assertEquals(meal.copy(photo = next), restored.meals.single())
        assertEquals(setOf(meal.id), restored.favoriteMealIds)
        store.cleanup(setOf(next))
        assertFalse(store.file(photo).exists())
        assertTrue(store.file(next).exists())
        store.erase()
        source.delete()
        Unit
    }

    @Test
    fun unsafeOrMissingArchivePhotosAreRejected() = runBlocking {
        val store = PhotoStore(ApplicationProvider.getApplicationContext())
        val original = "00000000-0000-0000-0000-000000000000.jpg"
        val metadata = backup(Meal(name = "Meal", photo = original))
        for (path in listOf("photos/../escape.jpg", "photos/$original")) {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("records.json"))
                z.write(json.encodeToString(metadata).toByteArray())
                z.closeEntry()
                z.putNextEntry(ZipEntry(path))
                z.write("not jpeg".toByteArray())
                z.closeEntry()
            }
            try {
                store.readBackup(ByteArrayInputStream(out.toByteArray()))
                fail("Invalid archive accepted")
            } catch (_: IllegalArgumentException) {}
        }
        assertFalse(
            File(ApplicationProvider.getApplicationContext<Application>().filesDir, "escape.jpg")
                .exists()
        )
    }

    @Test
    fun legacyJsonWithoutPhotosStillImports() = runBlocking {
        val store = PhotoStore(ApplicationProvider.getApplicationContext())
        val original = backup(Meal(name = "Old meal")).copy(version = 1)
        assertEquals(
            original,
            store.readBackup(ByteArrayInputStream(json.encodeToString(original).toByteArray())),
        )
    }
}
