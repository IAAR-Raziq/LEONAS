package com.leanos.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi", application = LeanosApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitText(text: String) {
        compose.waitUntil(15000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun onboardingMealLoggingNavigationAndRestart() {
        awaitText("A fresh start.")
        compose.onNodeWithText("Your name or nickname").performTextInput("Raziq")
        compose.onNodeWithText("Continue →").performScrollTo().performClick()
        compose.onNodeWithText("Continue →").performScrollTo().performClick()
        compose.onNodeWithText("Start my journey").performScrollTo().performClick()
        awaitText("Hey, Raziq.")
        // Snackbar overlays the bottom of a scrolled viewport; wait for its normal dismissal.
        awaitText("Saved")
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Saved").fetchSemanticsNodes().isEmpty()
        }
        val screenshotDir = File("build/outputs/screenshots").apply { mkdirs() }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(screenshotDir, "home-dark.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        compose.onNodeWithText("+ Add meal").performScrollTo().performClick()
        awaitText("Food name")
        compose.onNodeWithText("Food name").performTextInput("Nasi ayam")
        compose.onNodeWithText("Gallery").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Camera").assertIsDisplayed()
        compose.onNodeWithText("Calories per serving · kcal (optional)").assertDoesNotExist()
        compose.onNodeWithText("+ Add nutrition (optional)").performScrollTo().performClick()
        compose
            .onNodeWithText("Calories per serving · kcal (optional)")
            .performScrollTo()
            .performTextInput("250")
        compose.onNodeWithText("Save record").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Food name").fetchSemanticsNodes().isEmpty()
        }
        awaitText("Hey, Raziq.")
        compose.onNodeWithText("Meals").performClick()
        awaitText("Good food. Less friction.")
        compose.onNodeWithText("Nasi ayam", substring = false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Search meals").performTextInput("no match")
        compose
            .onNodeWithText("No meals match these filters.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Search meals").performTextClearance()
        compose.onNodeWithText("Nasi ayam", substring = false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Track").performClick()
        awaitText("Your daily rhythm")
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Saved").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Activity").performScrollTo().performClick()
        awaitText("Activity session")
        compose.onNodeWithText("Activity type").performScrollTo().performClick()
        compose.onNodeWithText("Strength").performClick()
        compose
            .onNodeWithContentDescription("Increase Duration · minutes")
            .performScrollTo()
            .performClick()
        compose
            .onNodeWithContentDescription("Increase Sets (optional)")
            .performScrollTo()
            .performClick()
        compose
            .onNodeWithContentDescription("Increase Reps per set (optional)")
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Save record").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Activity session").fetchSemanticsNodes().isEmpty()
        }
        awaitText("Saved")
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Saved").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Beginner strength · 20 min").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("+ 250 ml").performScrollTo().performClick()
        compose
            .onNodeWithText("Save day · partial entries welcome")
            .performScrollTo()
            .performClick()
        awaitText("Saved")
        compose.onNodeWithText("Progress").performClick()
        awaitText("The bigger picture")
        compose.onNodeWithText("Home").performClick()
        awaitText("Hey, Raziq.")
        compose.activityRule.scenario.recreate()
        awaitText("Hey, Raziq.")
    }
}
