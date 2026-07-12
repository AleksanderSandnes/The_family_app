@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.sandnes.familyapp.R

private val QUICK_REACTIONS = listOf("❤️", "😆", "😮", "😢", "😠", "👍")

@Composable
fun ReactionPickerPopup(
    onReact: (String) -> Unit,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn(tween(150)) + fadeIn(tween(150)),
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QUICK_REACTIONS.forEach { emoji ->
                            Box(
                                modifier =
                                    Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable { onReact(emoji) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(emoji, fontSize = 26.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    // Own-message actions (edit only applies to text messages).
                    if (onEdit != null || onDelete != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (onEdit != null) {
                                Text(
                                    stringResource(R.string.edit),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier =
                                        Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable(onClick = onEdit)
                                            .heightIn(min = 48.dp)
                                            .wrapContentHeight()
                                            .padding(horizontal = 16.dp),
                                )
                            }
                            if (onDelete != null) {
                                Text(
                                    stringResource(R.string.delete),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier =
                                        Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable(onClick = onDelete)
                                            .heightIn(min = 48.dp)
                                            .wrapContentHeight()
                                            .padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReactionChipsRow(
    reactions: Map<String, List<String>>,
    myReaction: String?,
    isMine: Boolean,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (reactions.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        reactions.entries.sortedByDescending { it.value.size }.take(5).forEach { (emoji, users) ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color =
                    if (emoji == myReaction) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                shadowElevation = 2.dp,
                modifier =
                    Modifier
                        .padding(end = 4.dp)
                        .clickable { onTap(emoji) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(emoji, fontSize = 14.sp)
                    if (users.size > 1) {
                        Text(
                            " ${users.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
