package com.sandnes.familyapp.util

import android.content.Context
import com.sandnes.familyapp.R

/**
 * Maps a caught exception to a short, user-friendly message — never the raw Postgrest/HTTP dump
 * (which leaks the request URL, headers and bearer/apikey and is unreadable). ViewModels that
 * surface errors to an `ErrorBanner` should route through this instead of `throwable.message`.
 */
fun friendlyErrorMessage(
    context: Context,
    throwable: Throwable?,
): String {
    val raw = throwable?.message.orEmpty()
    return when {
        raw.contains("duplicate key", ignoreCase = true) && raw.contains("name", ignoreCase = true) ->
            context.getString(R.string.that_name_is_already_taken)
        else -> context.getString(R.string.something_went_wrong)
    }
}
