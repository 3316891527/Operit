package com.ai.assistance.operit.data.theme.script

/** These limits describe different budgets: encoded bytes are not decoded image pixels. */
enum class ThemeResourceUnit { Bytes, Pixels, Count }

enum class ThemeResourceLimit(
    val maximum: Long,
    val label: String,
    val unit: ThemeResourceUnit,
) {
    AssetBytes(ThemeScriptContract.MAX_ASSET_BYTES.toLong(), "Single resource size", ThemeResourceUnit.Bytes),
    SvgBytes(ThemeScriptContract.MAX_SVG_BYTES.toLong(), "Single SVG size", ThemeResourceUnit.Bytes),
    BundleBytes(ThemeScriptContract.MAX_BUNDLE_BYTES.toLong(), "Script and resources total size", ThemeResourceUnit.Bytes),
    BitmapPixels(ThemeScriptContract.MAX_BITMAP_PIXELS, "Decoded bitmap pixel budget", ThemeResourceUnit.Pixels),
    AssetCount(ThemeScriptContract.MAX_ASSETS.toLong(), "Declared resource count", ThemeResourceUnit.Count),
    SourceBytes(ThemeScriptContract.MAX_SOURCE_BYTES.toLong(), "JavaScript source size", ThemeResourceUnit.Bytes),
    ManifestBytes(ThemeScriptContract.MAX_MANIFEST_BYTES.toLong(), "Manifest size", ThemeResourceUnit.Bytes),
    ResultBytes(ThemeScriptContract.MAX_RESULT_BYTES.toLong(), "Appearance result size", ThemeResourceUnit.Bytes),
    ArchiveBytes(ThemeScriptContract.MAX_BUNDLE_BYTES + 131072L, "Resource ZIP file size", ThemeResourceUnit.Bytes),
    ArchiveEntries(ThemeScriptContract.MAX_ASSETS + 2L, "Resource ZIP entry count", ThemeResourceUnit.Count);

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
) : IllegalArgumentException(buildString {
    append(budget.label)
    resource?.let { append(" ($it)") }
    append(" exceeded: limit=$maximum, used=$used, incoming=")
    if (incomingIsLowerBound) append("at least ")
    append(incoming)
    append(", total=")
    if (incomingIsLowerBound) append("at least ")
    append(used + incoming)
    append(when (budget.unit) {
        ThemeResourceUnit.Bytes -> " bytes"
        ThemeResourceUnit.Pixels -> " pixels"
        ThemeResourceUnit.Count -> " entries"
    })
}) {
    /** Import is additive, so split a validated candidate's total at the original project usage. */
    fun relativeTo(current: Long): ThemeResourceLimitException {
        require(current in 0L..(used + incoming))
        return ThemeResourceLimitException(budget, maximum, current, used + incoming - current, incomingIsLowerBound, resource)
    }
}

class ThemeResourceImportException(
    val resourceName: String,
    val currentBytes: Long,
    val currentAssets: Int,
    val fileBytes: Long?,
    val fileBytesIsLowerBound: Boolean,
    cause: Exception,
) : IllegalArgumentException("$resourceName: ${cause.message}", cause)
