package com.example.mainactivity.data

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FamilyRepositoryTest {
    // ──────────────────────────────────────────────────────────────
    // palette() — companion object, pure hash function
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `palette returns same color for same seed`() {
        val color1 = FamilyRepository.palette("alice")
        val color2 = FamilyRepository.palette("alice")
        assertEquals(color1, color2)
    }

    @Test
    fun `palette returns different colors for different seeds`() {
        val color1 = FamilyRepository.palette("alice")
        val color2 = FamilyRepository.palette("bob")
        assertNotEquals(color1, color2)
    }

    @Test
    fun `palette handles empty string without throwing`() {
        // Should not throw; result is a valid Int from the palette array
        val color = FamilyRepository.palette("")
        assertNotNull(color)
    }

    @Test
    fun `palette result is within the known palette range`() {
        // avatarColors has 8 entries; any result must be one of them
        val knownColors =
            setOf(
                0xFF6366F1.toInt(),
                0xFFEC4899.toInt(),
                0xFF14B8A6.toInt(),
                0xFFF59E0B.toInt(),
                0xFF8B5CF6.toInt(),
                0xFF06B6D4.toInt(),
                0xFFEF4444.toInt(),
                0xFF10B981.toInt(),
            )
        val result = FamilyRepository.palette("testSeed")
        assert(result in knownColors) {
            "palette(\"testSeed\") = $result is not in the known color set"
        }
    }

    // ──────────────────────────────────────────────────────────────
    // invalidateUserCache() — clears both private cache fields
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `invalidateUserCache clears cachedUser`() {
        val session = mockk<SessionManager>(relaxed = true)
        val repo = FamilyRepository(session)

        // Seed a non-null value via reflection
        val field = FamilyRepository::class.java.getDeclaredField("cachedUser")
        field.isAccessible = true
        field.set(repo, UserModel(id = "u1", name = "Alice", email = "a@b.com"))

        // Precondition: value is set
        assertNotNull(field.get(repo))

        repo.invalidateUserCache()

        assertNull("cachedUser should be null after invalidateUserCache()", field.get(repo))
    }

    @Test
    fun `invalidateUserCache clears cachedUserId`() {
        val session = mockk<SessionManager>(relaxed = true)
        val repo = FamilyRepository(session)

        val field = FamilyRepository::class.java.getDeclaredField("cachedUserId")
        field.isAccessible = true
        field.set(repo, "u1")

        assertNotNull(field.get(repo))

        repo.invalidateUserCache()

        assertNull("cachedUserId should be null after invalidateUserCache()", field.get(repo))
    }

    @Test
    fun `invalidateUserCache is idempotent when cache is already null`() {
        val session = mockk<SessionManager>(relaxed = true)
        val repo = FamilyRepository(session)

        // Both fields start as null — calling invalidate should not throw
        repo.invalidateUserCache()
        repo.invalidateUserCache()

        val userField = FamilyRepository::class.java.getDeclaredField("cachedUser")
        userField.isAccessible = true
        assertNull(userField.get(repo))

        val idField = FamilyRepository::class.java.getDeclaredField("cachedUserId")
        idField.isAccessible = true
        assertNull(idField.get(repo))
    }

    // ──────────────────────────────────────────────────────────────
    // currentUserId — delegates directly to SessionManager
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `currentUserId delegates to session`() {
        val session = mockk<SessionManager>(relaxed = true)
        val fakeFlow = flowOf("user-42")
        every { session.currentUserId } returns fakeFlow

        val repo = FamilyRepository(session)

        assertSame(
            "repo.currentUserId must be the exact same Flow instance returned by session",
            fakeFlow,
            repo.currentUserId,
        )
    }
}
