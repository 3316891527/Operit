package com.ai.assistance.operit.ui.common.format

/** Compact elapsed time shared by message statistics and execution titles. */
internal fun formatElapsedDuration(durationMs: Long): String {
    val millis = durationMs.coerceAtLeast(0L)
    if (millis < 1_000L) return "${millis}ms"

    // Round before splitting units so 59.995s carries to 1m instead of showing 60.00s.
    // Divide before adding the rounding increment to avoid overflowing Long durations.
    val centiseconds = millis / 10L + if (millis % 10L >= 5L) 1L else 0L
    val totalSeconds = centiseconds / 100L
    val days = totalSeconds / 86_400L
    val hours = totalSeconds / 3_600L % 24L
    val minutes = totalSeconds / 60L % 60L
    val seconds = totalSeconds % 60L
    val fraction = (centiseconds % 100L).toString().padStart(2, '0')

    return buildString {
        if (days > 0L) append(days).append('d')
        if (hours > 0L) append(hours).append('h')
        if (minutes > 0L) append(minutes).append('m')
        append(seconds).append('.').append(fraction).append('s')
    }
}
