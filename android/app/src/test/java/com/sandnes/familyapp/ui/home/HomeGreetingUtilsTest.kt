package com.sandnes.familyapp.ui.home

import com.sandnes.familyapp.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class HomeGreetingUtilsTest {
    private val validGreetings =
        setOf(R.string.good_morning, R.string.good_afternoon, R.string.good_evening)

    @Test
    fun timeBasedGreeting_returnsOneOfThreeValidResources() {
        // The function reads Calendar.HOUR_OF_DAY at call time, so we cannot
        // pin the result to a specific value. We can only assert it is one
        // of the three expected greeting string resources.
        val result = timeBasedGreeting()
        assertTrue(
            "Expected one of $validGreetings but got: '$result'",
            result in validGreetings,
        )
    }

    @Test
    fun timeBasedGreeting_isANonZeroResourceId() {
        val result = timeBasedGreeting()
        assertTrue("Greeting resource id must be non-zero", result != 0)
    }
}
