package com.sandnes.familyapp.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ErrorMessagesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun duplicateNameErrorsGetTheNameSpecificMessage() {
        val error = IllegalStateException("duplicate key value violates unique constraint \"families_name_key\"")
        assertEquals("That name is already taken.", friendlyErrorMessage(context, error))
    }

    @Test
    fun otherErrorsGetTheGenericMessageWithoutLeakingDetails() {
        val error = IllegalStateException("POST https://x.supabase.co/rest/v1/t apikey=secret failed")
        val message = friendlyErrorMessage(context, error)
        assertEquals("Something went wrong. Please try again.", message)
        assertFalse(message.contains("secret"))
    }

    @Test
    fun duplicateKeyWithoutANameColumnIsGeneric() {
        val error = IllegalStateException("duplicate key value violates unique constraint \"pk_1\"")
        assertEquals("Something went wrong. Please try again.", friendlyErrorMessage(context, error))
    }

    @Test
    fun nullAndMessagelessErrorsAreGeneric() {
        assertEquals("Something went wrong. Please try again.", friendlyErrorMessage(context, null))
        assertEquals("Something went wrong. Please try again.", friendlyErrorMessage(context, RuntimeException()))
    }
}
