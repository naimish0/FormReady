package com.rameshta.formready

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormReadyNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunches_andNavigatesToSettings() {
        val activity = composeRule.activity
        val homeTitle = activity.getString(R.string.home_title)
        val settingsNavigation = activity.getString(R.string.navigation_settings)
        val settingsTitle = activity.getString(R.string.settings_title)

        composeRule.onNodeWithText(homeTitle).assertIsDisplayed()
        composeRule.onNodeWithText(settingsNavigation).performClick()
        composeRule.onNode(hasText(settingsTitle) and isHeading()).assertIsDisplayed()
    }
}
