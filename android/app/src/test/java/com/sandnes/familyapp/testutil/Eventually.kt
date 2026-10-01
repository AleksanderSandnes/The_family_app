package com.sandnes.familyapp.testutil

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent

/**
 * Pumps the test scheduler until [condition] holds. The fake Supabase engine answers on a real IO
 * thread, so the loop alternates between running queued test coroutines and yielding real time to
 * the engine. It uses `runCurrent()` rather than `advanceUntilIdle()` on purpose: advancing virtual
 * time would fire Ktor's 30s request timeout before the real response arrives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestCoroutineScheduler.eventually(
    timeoutMs: Long = 30_000,
    message: String = "condition not met in ${timeoutMs}ms",
    condition: () -> Boolean,
) {
    val deadline = System.nanoTime() + timeoutMs * 1_000_000
    while (true) {
        runCurrent()
        if (condition()) return
        check(System.nanoTime() < deadline) { message }
        Thread.sleep(5)
    }
}
