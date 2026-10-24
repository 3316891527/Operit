package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.runtime.Composable
import com.ai.assistance.operit.ui.features.memory.workbench.MemorySpaceOverview

@Composable
fun MemoryOverviewScreen(onOpenSpace: (String) -> Unit, onOpenRecent: (String, String?) -> Unit) {
    MemorySpaceOverview(onOpenSpace, onOpenRecent)
}
