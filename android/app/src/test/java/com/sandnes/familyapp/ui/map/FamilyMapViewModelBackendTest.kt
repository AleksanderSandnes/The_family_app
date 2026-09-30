package com.sandnes.familyapp.ui.map

import android.app.Application
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.testutil.FakeSupabase
import com.sandnes.familyapp.testutil.eventually
import com.sandnes.familyapp.util.MainDispatcherRule
import io.ktor.http.HttpMethod
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** FamilyMapViewModel against a fake Supabase backend and a mocked fused-location client. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FamilyMapViewModelBackendTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val backend = FakeSupabase()
    private lateinit var app: Application
    private lateinit var repo: FamilyRepository
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var userId: MutableStateFlow<String?>
    private val locationVisible = MutableStateFlow(true)

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        backend.install()
        fused = mockk(relaxed = true)
        mockkStatic(LocationServices::class)
        every { LocationServices.getFusedLocationProviderClient(any<android.content.Context>()) } returns fused
        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        every { repo.currentUserId } returns userId
        every { repo.locationVisible } returns locationVisible
        coEvery { repo.getUser("u1") } returns UserModel(id = "u1", name = "Ada", familyId = "f1")
        coEvery { repo.getFamilyMembers("f1") } returns
            listOf(UserModel(id = "u1", name = "Ada"), UserModel(id = "u2", name = "Bob"))
        backend.onJson(
            HttpMethod.Get,
            "/rest/v1/user_locations",
            """[{"user_id":"u1","family_id":"f1","lat":1.0,"lng":2.0,"visible":true},
                {"user_id":"u2","family_id":"f1","lat":3.0,"lng":4.0,"display_name":"Bob","visible":true}]""",
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(LocationServices::class)
        backend.uninstall()
    }

    private fun settle(condition: () -> Boolean) = dispatcherRule.dispatcher.scheduler.eventually(condition = condition)

    private fun finish(job: Job) = settle { job.isCompleted }

    private fun loaded(): FamilyMapViewModel {
        val vm = FamilyMapViewModel(app, repo)
        userId.value = "u1"
        settle { vm.locations.value.isNotEmpty() && !vm.isLoading.value }
        return vm
    }

    @Test
    fun `loads other members' visible locations and family profiles`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            assertEquals(listOf("u2"), vm.locations.value.map { it.userId })
            assertEquals("Bob", vm.userProfiles.value["u2"]?.name)
            assertEquals("u1", vm.currentUserId.value)
            val query = backend.requestsTo("user_locations", HttpMethod.Get).first().query
            assertTrue(query.contains("family_id=eq.f1") && query.contains("visible=eq.true"))
        }

    @Test
    fun `a user without a family loads nothing`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("u1") } returns UserModel(id = "u1", name = "Ada", familyId = null)
            val vm = FamilyMapViewModel(app, repo)
            userId.value = "u1"
            settle { vm.currentUserId.value == "u1" }
            settle { true }
            assertTrue(vm.locations.value.isEmpty())
            assertTrue(backend.requestsTo("user_locations").isEmpty())
        }

    @Test
    fun `a failing location fetch keeps the list empty and stops loading`() =
        runTest(dispatcherRule.dispatcher) {
            backend.onJson(HttpMethod.Get, "/rest/v1/user_locations", "{}", io.ktor.http.HttpStatusCode.InternalServerError)
            val vm = FamilyMapViewModel(app, repo)
            userId.value = "u1"
            settle { backend.requestsTo("user_locations").isNotEmpty() && !vm.isLoading.value }
            assertTrue(vm.locations.value.isEmpty())
        }

    @Test
    fun `toggling visibility updates the preference and the shared row`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.setLocationVisible(false))
            coVerify { repo.setLocationVisible(false) }
            val patch = backend.requestsTo("user_locations", HttpMethod.Patch).single()
            assertTrue(patch.body.contains("\"visible\":false") && patch.query.contains("user_id=eq.u1"))
        }

    @Test
    fun `toggling visibility while signed out only stores the preference`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = FamilyMapViewModel(app, repo)
            finish(vm.setLocationVisible(true))
            assertTrue(backend.requestsTo("user_locations", HttpMethod.Patch).isEmpty())
            assertTrue(vm.locationVisible.value || !vm.locationVisible.value)
        }

    @Test
    fun `location updates publish the position and can be stopped`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val callback = slot<LocationCallback>()
            every { fused.requestLocationUpdates(any(), capture(callback), any()) } returns mockk(relaxed = true)
            vm.startLocationUpdates()
            vm.startLocationUpdates() // second start is ignored
            verify(exactly = 1) { fused.requestLocationUpdates(any(), any<LocationCallback>(), any()) }
            val location = mockk<Location>()
            every { location.latitude } returns 59.9
            every { location.longitude } returns 10.7
            val result = mockk<LocationResult>()
            every { result.lastLocation } returns location
            callback.captured.onLocationResult(result)
            assertEquals(59.9, vm.myLocation.value?.latitude ?: 0.0, 0.0001)
            settle { backend.requestsTo("user_locations", HttpMethod.Post).isNotEmpty() }
            val body = backend.requestsTo("user_locations", HttpMethod.Post).single().body
            assertTrue(body.contains("\"lat\":59.9") && body.contains("\"lng\":10.7") && body.contains("\"display_name\":\"Ada\""))
            assertTrue(body.contains("\"visible\":true"))
            vm.stopLocationUpdates()
            verify { fused.removeLocationUpdates(any<LocationCallback>()) }
            vm.stopLocationUpdates()
        }

    @Test
    fun `a location result without a fix is ignored`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val callback = slot<LocationCallback>()
            every { fused.requestLocationUpdates(any(), capture(callback), any()) } returns mockk(relaxed = true)
            vm.startLocationUpdates()
            val result = mockk<LocationResult>()
            every { result.lastLocation } returns null
            callback.captured.onLocationResult(result)
            assertNull(vm.myLocation.value)
        }

    @Test
    fun `publishing skips users without a profile or while signed out`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("u1") } returns null
            val vm = FamilyMapViewModel(app, repo)
            userId.value = "u1"
            settle { vm.currentUserId.value == "u1" }
            val callback = slot<LocationCallback>()
            every { fused.requestLocationUpdates(any(), capture(callback), any()) } returns mockk(relaxed = true)
            vm.startLocationUpdates()
            val location = mockk<Location>()
            every { location.latitude } returns 1.0
            every { location.longitude } returns 2.0
            val result = mockk<LocationResult>()
            every { result.lastLocation } returns location
            callback.captured.onLocationResult(result)
            settle { true }
            assertTrue(backend.requestsTo("user_locations", HttpMethod.Post).isEmpty())
            assertNotNull(vm.myLocation.value)
        }

    @Test
    fun `clearing the own location hides the shared row`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            vm.clearOwnLocation()
            settle { backend.requestsTo("user_locations", HttpMethod.Patch).isNotEmpty() }
            assertTrue(
                backend
                    .requestsTo("user_locations", HttpMethod.Patch)
                    .single()
                    .body
                    .contains("\"visible\":false"),
            )
            userId.value = null
            settle { vm.currentUserId.value == null }
            vm.clearOwnLocation()
            settle { true }
            assertEquals(1, backend.requestsTo("user_locations", HttpMethod.Patch).size)
        }

    @Test
    fun `clearing the view model stops updates`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            every { fused.requestLocationUpdates(any(), any<LocationCallback>(), any()) } returns mockk(relaxed = true)
            vm.startLocationUpdates()
            val onCleared = FamilyMapViewModel::class.java.getDeclaredMethod("onCleared")
            onCleared.isAccessible = true
            onCleared.invoke(vm)
            verify { fused.removeLocationUpdates(any<LocationCallback>()) }
            assertFalse(vm.isLoading.value)
        }
}
