package com.sandnes.familyapp.testutil

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider

/** Helpers shared by the Robolectric Compose screen tests. */
val appContext: Context get() = ApplicationProvider.getApplicationContext()

fun str(
    @StringRes id: Int,
    vararg args: Any,
): String = appContext.getString(id, *args)

fun ComposeContentTestRule.waitForText(
    text: String,
    timeoutMs: Long = 20_000,
    substring: Boolean = false,
) {
    waitUntil(timeoutMs) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
}

fun ComposeContentTestRule.hasText(
    text: String,
    substring: Boolean = false,
) = onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

fun ComposeContentTestRule.hasDescription(text: String) =
    onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()

/** Clicks the [index]th node showing [text] (several screens repeat a label in a FAB and an action). */
fun ComposeContentTestRule.clickText(
    text: String,
    index: Int = 0,
    substring: Boolean = false,
) {
    onAllNodesWithText(text, substring = substring)[index].click()
    waitForIdle()
}

fun ComposeContentTestRule.clickDescription(
    text: String,
    index: Int = 0,
) {
    onAllNodesWithContentDescription(text)[index].click()
    waitForIdle()
}

/** Types into the [index]th editable field currently on screen. */
fun ComposeContentTestRule.typeInto(
    text: String,
    index: Int = 0,
) {
    onAllNodes(hasSetTextAction())[index].performTextInput(text)
    waitForIdle()
}

/** Runs the first custom accessibility action of a node (used for swipe-to-delete rows). */
fun SemanticsNodeInteraction.runCustomAction(label: String? = null) {
    val node: SemanticsNode = fetchSemanticsNode()
    val actions = node.config[SemanticsActions.CustomActions]
    (if (label == null) actions.first() else actions.first { it.label == label }).action()
}

/** Fires the node's click action through semantics (robust against clipped/animating windows), else a touch click. */
fun SemanticsNodeInteraction.click() {
    if (fetchSemanticsNode().config.contains(SemanticsActions.OnClick)) {
        performSemanticsAction(SemanticsActions.OnClick)
    } else {
        performClick()
    }
}
