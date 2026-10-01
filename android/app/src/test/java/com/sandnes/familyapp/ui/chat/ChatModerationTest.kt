package com.sandnes.familyapp.ui.chat

import com.sandnes.familyapp.data.MessageModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatModerationTest {
    private fun msg(
        id: String,
        from: String,
        type: String = "text",
    ) = MessageModel(id = id, conversationId = "c1", userFrom = from, text = "fictional", messageType = type)

    @Test
    fun `blocked senders are hidden but system messages remain`() {
        val list = listOf(msg("1", "emma"), msg("2", "lars"), msg("3", "lars", "system"), msg("4", "nora"))
        assertEquals(listOf("1", "3", "4"), visibleMessages(list, setOf("lars")).map { it.id })
    }

    @Test
    fun `no blocks returns the same list`() {
        val list = listOf(msg("1", "emma"))
        assertSame(list, visibleMessages(list, emptySet()))
    }

    @Test
    fun `only other people's persisted messages can be moderated`() {
        assertTrue(canModerate(msg("1", "lars"), "emma"))
        assertFalse(canModerate(msg("1", "emma"), "emma"))
        assertFalse(canModerate(msg("1", "lars"), null))
        assertFalse(canModerate(msg("1", "lars", "system"), "emma"))
        assertFalse(canModerate(msg("temp-1", "lars"), "emma"))
    }

    @Test
    fun `report reasons match the database check constraint`() {
        assertEquals(
            listOf("spam", "harassment", "inappropriate", "other"),
            ReportReason.entries.map { it.dbValue },
        )
    }
}
