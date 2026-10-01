package com.sandnes.familyapp.testutil

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText as hasTextMatcher
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
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

/** Clicks the icon-only button that sits next to the node showing [text] (checkboxes, toggles, menu buttons). */
fun ComposeContentTestRule.clickButtonBeside(
    text: String,
    index: Int = 0,
) {
    onAllNodesWithText(text)[index]
        .onParent()
        .onChildren()
        .filter(hasClickAction() and SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))[0]
        .click()
    waitForIdle()
}

/** Runs the [nth] custom accessibility action found on screen (the swipe-to-delete rows), on the UI thread. */
fun ComposeContentTestRule.runSwipeDelete(nth: Int = 0) {
    val node = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))[nth].fetchSemanticsNode()
    runOnUiThread { node.config[SemanticsActions.CustomActions].first().action() }
    waitForIdle()
}

/** Sends the keyboard "done" action to the [index]th editable field. */
fun ComposeContentTestRule.imeDone(index: Int = 0) {
    onAllNodes(hasSetTextAction())[index].performImeAction()
    waitForIdle()
}

fun ComposeContentTestRule.replaceText(
    text: String,
    index: Int = 0,
) {
    onAllNodes(hasSetTextAction())[index].performTextReplacement(text)
    waitForIdle()
}

/** Replaces the text of the editable field currently showing [current] and optionally sends the keyboard "done". */
fun ComposeContentTestRule.editField(
    current: String,
    replacement: String,
    done: Boolean = true,
) {
    val field = onAllNodes(hasSetTextAction() and hasTextMatcher(current))[0]
    field.performTextReplacement(replacement)
    waitForIdle()
    if (done) {
        onAllNodes(hasSetTextAction() and hasTextMatcher(replacement))[0].performImeAction()
        waitForIdle()
    }
}

/** Flips the [index]th switch/checkbox currently on screen. */
fun ComposeContentTestRule.clickToggle(index: Int = 0) {
    onAllNodes(androidx.compose.ui.test.isToggleable())[index].click()
    waitForIdle()
}

fun SemanticsNodeInteraction.performImeActionForTest() {
    performImeAction()
}

fun SemanticsNodeInteraction.performTextReplacementForTest(text: String) {
    performTextReplacement(text)
}
