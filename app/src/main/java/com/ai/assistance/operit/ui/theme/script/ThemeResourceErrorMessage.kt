package com.ai.assistance.operit.ui.theme.script

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.theme.script.ThemeResourceImportException
import com.ai.assistance.operit.data.theme.script.ThemeResourceLimit
import com.ai.assistance.operit.data.theme.script.ThemeResourceLimitException
import com.ai.assistance.operit.data.theme.script.ThemeResourceUnit
import java.text.NumberFormat
import java.util.Locale

internal fun themeResourceErrorMessage(context: Context, failure: Throwable): String {
    if (failure is ThemeResourceImportException) {
        val fileSize = failure.fileBytes?.let {
            val value = themeResourceBytes(context, it)
            if (failure.fileBytesIsLowerBound) context.getString(R.string.theme_resource_at_least, value) else value
        } ?: context.getString(R.string.theme_resource_size_unknown)
        return listOf(
            failure.resourceName,
            themeResourceErrorMessage(context, requireNotNull(failure.cause)),
            context.getString(R.string.studio_resource_import_usage,
                themeResourceBytes(context, failure.currentBytes),
                themeResourceBytes(context, ThemeResourceLimit.BundleBytes.maximum),
                failure.currentAssets, ThemeResourceLimit.AssetCount.maximum.toInt()),
            context.getString(R.string.studio_resource_import_file_size, fileSize),
        ).joinToString("\n")
    }
    if (failure !is ThemeResourceLimitException) {
        // ThemeArchive retains the concrete budget failure as the invalid-archive cause.
        val budgetFailure = generateSequence(failure.cause) { it.cause }
            .filterIsInstance<ThemeResourceLimitException>().firstOrNull()
        if (budgetFailure != null) return themeResourceErrorMessage(context, budgetFailure)
        return failure.message.orEmpty()
    }
    val label = context.getString(when (failure.budget) {
        ThemeResourceLimit.AssetBytes -> R.string.theme_resource_limit_asset_bytes
        ThemeResourceLimit.SvgBytes -> R.string.theme_resource_limit_svg_bytes
        ThemeResourceLimit.BundleBytes -> R.string.theme_resource_limit_bundle_bytes
        ThemeResourceLimit.BitmapPixels -> R.string.theme_resource_limit_bitmap_pixels
        ThemeResourceLimit.AssetCount -> R.string.theme_resource_limit_asset_count
        ThemeResourceLimit.SourceBytes -> R.string.theme_resource_limit_source_bytes
        ThemeResourceLimit.ManifestBytes -> R.string.theme_resource_limit_manifest_bytes
        ThemeResourceLimit.ResultBytes -> R.string.theme_resource_limit_result_bytes
        ThemeResourceLimit.ArchiveBytes -> R.string.theme_resource_limit_archive_bytes
        ThemeResourceLimit.ArchiveEntries -> R.string.theme_resource_limit_archive_entries
    })
    val numbers = NumberFormat.getIntegerInstance(resourceLocale(context))
    fun amount(value: Long): String = when (failure.budget.unit) {
        ThemeResourceUnit.Bytes -> themeResourceBytes(context, value)
        ThemeResourceUnit.Pixels -> context.getString(R.string.theme_resource_pixels, numbers.format(value))
        ThemeResourceUnit.Count -> numbers.format(value)
    }
    fun incoming(value: Long): String = if (failure.incomingIsLowerBound) {
        context.getString(R.string.theme_resource_at_least, amount(value))
    } else amount(value)
    val details = context.getString(R.string.theme_resource_limit_details, label,
        amount(failure.maximum), amount(failure.used), incoming(failure.incoming), incoming(failure.used + failure.incoming))
    return listOfNotNull(failure.resource, details).joinToString("\n")
}

internal fun themeResourceBytes(context: Context, bytes: Long): String {
    val locale = resourceLocale(context)
    val exact = NumberFormat.getIntegerInstance(locale).format(bytes) + " B"
    val (scale, unit) = when {
        bytes >= 1024L * 1024L -> 1024L * 1024L to "MiB"
        bytes >= 1024L -> 1024L to "KiB"
        else -> return exact
    }
    val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }
    return "${number.format(bytes.toDouble() / scale)} $unit ($exact)"
}

private fun resourceLocale(context: Context): Locale = context.resources.configuration.locales[0]
