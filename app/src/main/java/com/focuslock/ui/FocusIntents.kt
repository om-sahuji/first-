package com.focuslock.ui

import android.content.Context
import android.content.Intent
import com.focuslock.ui.MainActivity

/** Intents used by enforcement code to bring the Focus environment to the front. */
object FocusIntents {

    const val EXTRA_BLOCKED_PACKAGE = "com.focuslock.extra.BLOCKED_PACKAGE"

    fun focus(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
    }
}
