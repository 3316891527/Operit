package com.ai.assistance.operit.ui.features.packages.management

import android.content.Context

class PluginListPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "plugin_list_presentation", Context.MODE_PRIVATE
    )

    fun readSort(): PluginSortSelection = PluginSortSelection(
        mode = PluginSortMode.valueOf(preferences.getString("mode", PluginSortMode.CUSTOM.name)!!),
        descending = preferences.getBoolean("descending", false)
    )

    fun readFilter(): PluginFilter =
        PluginFilter.valueOf(preferences.getString("filter", PluginFilter.ALL.name)!!)

    fun saveSort(selection: PluginSortSelection) {
        preferences.edit()
            .putString("mode", selection.mode.name)
            .putBoolean("descending", selection.descending)
            .apply()
    }

    fun saveFilter(filter: PluginFilter) {
        preferences.edit().putString("filter", filter.name).apply()
    }
}
