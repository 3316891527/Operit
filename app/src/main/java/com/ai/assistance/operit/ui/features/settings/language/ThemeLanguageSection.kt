package com.ai.assistance.operit.ui.features.settings.language

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsSectionTitle
import com.ai.assistance.operit.util.LocaleUtils

/** 语言属于应用全局配置，不进入角色主题的保存、重置和应用到全部角色的数据。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeLanguageSection(
    cardColors: CardColors,
    enabled: Boolean,
    onLanguageSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { UserPreferencesManager.getInstance(context) }
    val storedCode by preferences.appLanguage.collectAsState(initial = preferences.getCurrentLanguage())
    val supported = remember { LocaleUtils.getSupportedLanguages() }
    val selectedCode = remember(context, storedCode) {
        if (storedCode.isBlank() || storedCode == LocaleUtils.LanguageCodes.AUTO) LocaleUtils.LanguageCodes.AUTO
        else LocaleUtils.getCurrentLanguage(context)
    }
    val followSystem = stringResource(R.string.language_follow_system)
    fun label(language: LocaleUtils.Language): String =
        if (language.code == LocaleUtils.LanguageCodes.AUTO) followSystem else language.nativeName
    val selected = supported.first { it.code == selectedCode }
    var expanded by remember { mutableStateOf(false) }

    ThemeSettingsSectionTitle(stringResource(R.string.language_settings), Icons.Default.Language)
    Card(Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ExposedDropdownMenuBox(expanded = expanded,
                onExpandedChange = { if (enabled) expanded = it }) {
                OutlinedTextField(
                    value = label(selected), onValueChange = {}, readOnly = true, enabled = enabled,
                    label = { Text(stringResource(R.string.select_language)) },
                    leadingIcon = { Icon(Icons.Default.Language, null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                    modifier = Modifier.heightIn(max = 360.dp)) {
                    supported.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(label(language)) },
                            trailingIcon = {
                                if (language.code == selectedCode) Icon(Icons.Default.Check, null)
                            },
                            onClick = {
                                expanded = false
                                if (enabled && language.code != selectedCode) onLanguageSelected(language.code)
                            },
                        )
                    }
                }
            }
            Text(stringResource(R.string.language_global_info), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
