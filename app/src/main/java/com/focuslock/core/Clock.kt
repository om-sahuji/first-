package com.focuslock.core

import java.time.ZoneId

/** Indirection over the clock so time-dependent rules can be tested deterministically. */
interface Clock {
    fun nowMillis(): Long

    fun zone(): ZoneId
}

object SystemTime : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
