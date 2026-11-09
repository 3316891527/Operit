package com.ai.assistance.operit.ui.theme.script

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.theme.script.ThemeResourceLimit
import com.ai.assistance.operit.data.theme.script.themeResourceLimitFailure

/** The persistent banner keeps the English reason and only the attempted total / limit. */
internal fun themeResourceErrorMessage(failure: Throwable): String {
    val budgetFailure = failure.themeResourceLimitFailure()
    if (budgetFailure != null) return budgetFailure.message.orEmpty()
    return failure.message.orEmpty()
}

/** Only user action failures show a toast; preview recomposition keeps its existing error surface. */
internal fun themeResourceErrorToast(context: Context, failure: Throwable): String? {
    val budgetFailure = failure.themeResourceLimitFailure() ?: return null
    return context.getString(when (budgetFailure.budget) {
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
}
