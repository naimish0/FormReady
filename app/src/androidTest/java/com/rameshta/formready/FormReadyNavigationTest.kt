package com.rameshta.formready

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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

    @Test
    fun photoFlowShowsExplicitRequirementsAndPrivateSelection() {
        val activity = composeRule.activity

        composeRule.onNodeWithText(
            activity.getString(R.string.capability_photo_title),
        ).performClick()

        composeRule.onNodeWithText(activity.getString(R.string.photo_title)).assertIsDisplayed()
        composeRule.onNodeWithText(
            activity.getString(R.string.requirement_byte_unit),
        ).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasText(activity.getString(R.string.photo_choose)),
        )
        composeRule.onNodeWithText(activity.getString(R.string.photo_choose)).assertIsDisplayed()
    }
}
