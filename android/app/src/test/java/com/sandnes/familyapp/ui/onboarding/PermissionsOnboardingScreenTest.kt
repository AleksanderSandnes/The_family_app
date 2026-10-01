package com.sandnes.familyapp.ui.onboarding

import android.Manifest
import android.app.Application
import com.sandnes.familyapp.R
import com.sandnes.familyapp.testutil.ComposeScreenTest
import com.sandnes.familyapp.testutil.appContext
import com.sandnes.familyapp.testutil.clickDescription
import com.sandnes.familyapp.testutil.clickText
import com.sandnes.familyapp.testutil.hasDescription
import com.sandnes.familyapp.testutil.str
import com.sandnes.familyapp.testutil.waitForText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf

class PermissionsOnboardingScreenTest : ComposeScreenTest() {
    private val titles = listOf(R.string.notifications, R.string.location, R.string.camera, R.string.microphone)

    @Test
    fun `ungranted permissions can be requested one by one and skipped`() {
        var done = 0
        compose.setContent { PermissionsOnboardingScreen(onComplete = { done++ }) }
        compose.waitForText(str(R.string.before_we_start))
        compose.waitUntil(20_000) { compose.hasDescription(str(R.string.permission_not_granted_a11y, str(R.string.microphone))) }
        for (title in titles) {
            compose.clickDescription(str(R.string.permission_not_granted_a11y, str(title)))
        }
        // Continue asks for everything still missing in one go.
        compose.clickText(str(R.string.continue_label))
        compose.clickText(str(R.string.skip_for_now))
        assertTrue(done >= 1)
    }

    @Test
    fun `when everything is granted continue completes immediately`() {
        shadowOf(appContext as Application).grantPermissions(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
        var done = 0
        compose.setContent { PermissionsOnboardingScreen(onComplete = { done++ }) }
        compose.waitUntil(20_000) { compose.hasDescription(str(R.string.permission_granted_a11y, str(R.string.microphone))) }
        assertTrue(compose.hasDescription(str(R.string.permission_granted_a11y, str(R.string.notifications))))
        compose.clickText(str(R.string.continue_label))
        assertEquals(1, done)
    }
}
