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
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasText(activity.getString(R.string.settings_clear_temporary)),
        )
        composeRule.onNodeWithText(activity.getString(R.string.settings_clear_temporary))
            .assertIsDisplayed()
    }

    @Test
    fun presetsSupportCreateImportAndGenericRules() {
        val activity = composeRule.activity
        composeRule.onNodeWithText(
            activity.getString(R.string.navigation_presets),
        ).performClick()

        composeRule.onNodeWithText(activity.getString(R.string.presets_title)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.presets_create)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.presets_import)).assertIsDisplayed()
        composeRule.onNodeWithText(
            activity.getString(R.string.preset_builtin_photo_600x800),
        ).assertIsDisplayed()
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

    @Test
    fun signatureFlowOffersGalleryCameraAndDrawingWithoutStoragePermission() {
        val activity = composeRule.activity
        composeRule.onNodeWithText(
            activity.getString(R.string.capability_signature_title),
        ).performClick()

        composeRule.onNodeWithText(activity.getString(R.string.signature_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.signature_gallery))
            .assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.signature_camera))
            .assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.signature_draw))
            .assertIsDisplayed()
    }

    @Test
    fun pdfFlowOffersInspectionImagesAndHonestCompression() {
        val activity = composeRule.activity
        composeRule.onNodeWithText(
            activity.getString(R.string.capability_pdf_title),
        ).performClick()

        composeRule.onNodeWithText(activity.getString(R.string.pdf_title)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.pdf_choose)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.pdf_images_to_pdf))
            .assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.pdf_page_operations_choose))
            .assertIsDisplayed()
    }

    @Test
    fun batchCapabilityOpensFunctionalBatchFlow() {
        val activity = composeRule.activity
        composeRule.onNodeWithText(
            activity.getString(R.string.capability_batch_title),
        ).performClick()

        composeRule.onNodeWithText(activity.getString(R.string.batch_title)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.batch_choose)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.batch_privacy_and_limit, 10))
            .assertIsDisplayed()
    }
}
