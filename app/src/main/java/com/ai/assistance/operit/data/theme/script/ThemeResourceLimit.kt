package com.ai.assistance.operit.data.theme.script

import java.text.NumberFormat
import java.util.Locale

/** These limits describe different budgets: encoded bytes are not decoded image pixels. */
enum class ThemeResourceUnit { Bytes, Pixels, Count }

enum class ThemeResourceLimit(
    val maximum: Long,
    val reason: String,
    val unit: ThemeResourceUnit,
) {
    AssetBytes(ThemeScriptContract.MAX_ASSET_BYTES.toLong(), "Theme script resource exceeds size limit", ThemeResourceUnit.Bytes),
    SvgBytes(ThemeScriptContract.MAX_SVG_BYTES.toLong(), "Theme SVG is too large", ThemeResourceUnit.Bytes),
    BundleBytes(ThemeScriptContract.MAX_BUNDLE_BYTES.toLong(), "Theme script bundle is too large", ThemeResourceUnit.Bytes),
    BitmapPixels(ThemeScriptContract.MAX_BITMAP_PIXELS, "Theme drawing assets exceed the decoded pixel budget", ThemeResourceUnit.Pixels),
    AssetCount(ThemeScriptContract.MAX_ASSETS.toLong(), "Theme script declares too many assets", ThemeResourceUnit.Count),
    SourceBytes(ThemeScriptContract.MAX_SOURCE_BYTES.toLong(), "Theme script is too large", ThemeResourceUnit.Bytes),
    ManifestBytes(ThemeScriptContract.MAX_MANIFEST_BYTES.toLong(), "Theme script manifest is too large", ThemeResourceUnit.Bytes),
    ResultBytes(ThemeScriptContract.MAX_RESULT_BYTES.toLong(), "Theme script result exceeds size limit", ThemeResourceUnit.Bytes),
    ArchiveBytes(ThemeScriptContract.MAX_BUNDLE_BYTES + 131072L, "Theme script ZIP exceeds size limit", ThemeResourceUnit.Bytes),
    ArchiveEntries(ThemeScriptContract.MAX_ASSETS + 2L, "Theme script ZIP declares too many entries", ThemeResourceUnit.Count);

    fun check(incoming: Long, used: Long = 0L, resource: String? = null) {
        require(incoming >= 0L && used >= 0L)
        if (used > maximum || incoming > maximum - used) {
            throw ThemeResourceLimitException(this, maximum, used, incoming, resource = resource)
        }
    }
}

/** A bounded read may only know a lower bound; never report it as the complete file size. */
class ThemeResourceLimitException(
    val budget: ThemeResourceLimit,
    val maximum: Long,
    val used: Long,
    val incoming: Long,
    val incomingIsLowerBound: Boolean = false,
    val resource: String? = null,
) : IllegalArgumentException(resourceLimitSummary(budget, maximum, used + incoming, incomingIsLowerBound))

/** Unwrap archive validation without adding file names or per-import bookkeeping to the banner. */
internal fun Throwable.themeResourceLimitFailure(): ThemeResourceLimitException? =
    generateSequence(this) { it.cause }.filterIsInstance<ThemeResourceLimitException>().firstOrNull()

private fun resourceLimitSummary(
    budget: ThemeResourceLimit,
    maximum: Long,
    total: Long,
    isLowerBound: Boolean,
): String {
    val numbers = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }
    var scale = if (budget.unit == ThemeResourceUnit.Bytes) when {
        maximum >= 1024L * 1024L -> 1024L * 1024L
        maximum >= 1024L -> 1024L
        else -> 1L
    } else 1L
    fun amount(value: Long): String = numbers.format(value.toDouble() / scale)
    // A one-byte overrun must not round to the same displayed value as its limit.
    if (total != maximum && amount(total) == amount(maximum)) scale = 1L
    val suffix = if (budget.unit == ThemeResourceUnit.Bytes) when (scale) {
        1024L * 1024L -> " MiB"
        1024L -> " KiB"
        else -> " B"
    } else ""
    val lowerBound = if (isLowerBound) "≥" else ""
    return "${budget.reason} ($lowerBound${amount(total)}$suffix/${amount(maximum)}$suffix)"
}
