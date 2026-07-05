package com.example.mainactivity.ui.home

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class HomeGreetingUtilsTest {
    private val validGreetings = setOf("Good morning", "Good afternoon", "Good evening")

    @Test
    fun timeBasedGreeting_returnsOneOfThreeValidValues() {
        // The function reads Calendar.HOUR_OF_DAY at call time, so we cannot
        // pin the result to a specific value. We can only assert it is one
        // of the three expected strings.
        val result = timeBasedGreeting()
        assertTrue(
            "Expected one of $validGreetings but got: '$result'",
            result in validGreetings,
        )
    }

    @Test
    fun timeBasedGreeting_isNotEmpty() {
        val result = timeBasedGreeting()
        assertTrue("Greeting must not be empty", result.isNotEmpty())
    }

    @Test
    fun timeBasedGreeting_startsWithGood() {
        val result = timeBasedGreeting()
        assertTrue("Greeting must start with 'Good'", result.startsWith("Good"))
    }
}
