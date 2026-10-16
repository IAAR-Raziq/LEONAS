package com.leanos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = LeanosApplication::class)
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
        compose.onNodeWithText("+ Add meal").performScrollTo().performClick()
        awaitText("Food name")
        compose.onNodeWithText("Food name").performTextInput("Nasi ayam")
        compose.onNodeWithText("Save record").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Food name").fetchSemanticsNodes().isEmpty()
        }
        awaitText("Hey, Raziq.")
        compose.onNodeWithText("Meals").performClick()
        awaitText("Good food. Less friction.")
        compose.onNodeWithText("Nasi ayam", substring = false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Progress").performClick()
        awaitText("The bigger picture")
        compose.onNodeWithText("Home").performClick()
        awaitText("Hey, Raziq.")
        compose.activityRule.scenario.recreate()
        awaitText("Hey, Raziq.")
    }
}
