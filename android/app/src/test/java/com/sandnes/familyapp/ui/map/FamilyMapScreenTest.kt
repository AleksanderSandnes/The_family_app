package com.sandnes.familyapp.ui.map

import android.Manifest
import android.app.Application
import com.google.android.gms.maps.model.LatLng
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.UserLocationModel
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.testutil.ComposeScreenTest
import com.sandnes.familyapp.testutil.appContext
import com.sandnes.familyapp.testutil.clickDescription
import com.sandnes.familyapp.testutil.clickText
import com.sandnes.familyapp.testutil.clickToggle
import com.sandnes.familyapp.testutil.hasText
import com.sandnes.familyapp.testutil.str
import com.sandnes.familyapp.testutil.waitForText
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import java.time.Instant

class FamilyMapScreenTest : ComposeScreenTest() {
    @Before
    fun stubMapsSdk() {
        // The Maps SDK factories need Play services; give them inert stand-ins.
        mockkStatic(BitmapDescriptorFactory::class, CameraUpdateFactory::class)
        every { BitmapDescriptorFactory.fromBitmap(any()) } returns mockk<BitmapDescriptor>(relaxed = true)
        every { CameraUpdateFactory.newLatLngZoom(any(), any()) } returns mockk<CameraUpdate>(relaxed = true)
        every { CameraUpdateFactory.newLatLngBounds(any(), any()) } returns mockk<CameraUpdate>(relaxed = true)
    }

    // The static stubs are deliberately left in place: marker bitmaps are built on a background
    // dispatcher that can still be running when a test ends, and must never reach the real SDK.

    private val myLocation = MutableStateFlow<LatLng?>(null)
    private val locations = MutableStateFlow<List<UserLocationModel>>(emptyList())
    private val profiles = MutableStateFlow<Map<String, UserModel>>(emptyMap())
    private val loading = MutableStateFlow(false)
    private val currentUser = MutableStateFlow<String?>("u1")
    private val visible = MutableStateFlow(true)

    private fun viewModel(): FamilyMapViewModel {
        val vm = mockk<FamilyMapViewModel>(relaxed = true)
        every { vm.myLocation } returns myLocation
        every { vm.locations } returns locations
        every { vm.userProfiles } returns profiles
        every { vm.isLoading } returns loading
        every { vm.currentUserId } returns currentUser
        every { vm.locationVisible } returns visible
        return vm
    }

    private val family =
        mapOf(
            "u1" to UserModel(id = "u1", name = "Ada", avatarColor = 0xFF0000FF.toInt()),
            "u2" to UserModel(id = "u2", name = "Bob"),
            "u3" to UserModel(id = "u3", name = "Cy", avatarUrl = "https://img.example/c.png"),
        )

    private fun grantLocation() {
        shadowOf(appContext as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Test
    fun `without permission a rationale is shown once and can be accepted`() {
        val vm = viewModel()
        compose.setContent { FamilyMapScreen(onBack = {}, viewModel = vm) }
        compose.waitForText(str(R.string.location_access))
        compose.clickText(str(R.string.allow))
    }

    @Test
    fun `declining the rationale shows how to enable location later`() {
        val vm = viewModel()
        compose.setContent { FamilyMapScreen(onBack = {}, viewModel = vm) }
        compose.waitForText(str(R.string.location_access))
        compose.clickText(str(R.string.not_now))
        compose.waitForText(str(R.string.you_can_enable_location_in_settings))
    }

    @Test
    fun `granted permission starts updates and a solo user sees the empty state`() {
        grantLocation()
        profiles.value = mapOf("u1" to family.getValue("u1"))
        val vm = viewModel()
        compose.setContent { FamilyMapScreen(onBack = {}, viewModel = vm) }
        compose.waitForText(str(R.string.just_you_here))
        verify(timeout = 20_000) { vm.startLocationUpdates() }
    }

    @Test
    fun `family members show in the legend with live and stale states`() {
        grantLocation()
        profiles.value = family
        myLocation.value = LatLng(59.9, 10.7)
        locations.value =
            listOf(
                UserLocationModel(userId = "u2", familyId = "f1", lat = 60.0, lng = 11.0, visible = true, updatedAt = Instant.now().toString()),
                UserLocationModel(userId = "u3", familyId = "f1", lat = 61.0, lng = 12.0, visible = true, updatedAt = Instant.now().minusSeconds(86_400).toString()),
            )
        val vm = viewModel()
        compose.setContent { FamilyMapScreen(onBack = {}, viewModel = vm) }
        compose.waitForText(str(R.string.on_the_map))
        compose.clickToggle(0)
        verify { vm.setLocationVisible(false) }
        compose.clickDescription(str(R.string.center_on_my_location))
    }

    @Test
    fun `members who are not sharing are dimmed and hidden sharing is explained`() {
        grantLocation()
        profiles.value = family
        visible.value = false
        locations.value = emptyList()
        val vm = viewModel()
        compose.setContent { FamilyMapScreen(onBack = {}, viewModel = vm) }
        compose.waitForText(str(R.string.you_hidden_on_map))
    }

    @Test
    fun `one shared pin and several pins both frame the camera`() {
        grantLocation()
        profiles.value = family
        locations.value = listOf(UserLocationModel(userId = "u2", familyId = "f1", lat = 60.0, lng = 11.0, visible = true))
        val vm = viewModel()
        compose.setContent { FamilyMapScreen(onBack = {}, viewModel = vm) }
        compose.waitForText(str(R.string.on_the_map))
        locations.value =
            listOf(
                UserLocationModel(userId = "u2", familyId = "f1", lat = 60.0, lng = 11.0, visible = true),
                UserLocationModel(userId = "u3", familyId = "f1", lat = 61.0, lng = 12.0, visible = true),
            )
        compose.waitForIdle()
    }

    @Test
    fun `loading with no pins shows the loading state`() {
        grantLocation()
        loading.value = true
        profiles.value = family
        val vm = viewModel()
        compose.setContent { FamilyMapScreen(onBack = {}, viewModel = vm) }
        compose.waitForIdle()
    }
}
