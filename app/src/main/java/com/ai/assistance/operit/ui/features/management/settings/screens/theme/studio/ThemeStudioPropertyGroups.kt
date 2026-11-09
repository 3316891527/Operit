package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

/** 按制作对象分组，折叠前提交数值输入；高级效果统一排在普通属性之后。 */
@Composable
internal fun StudioPropertyGroup(
    state: ThemeStudioState,
    id: String,
    title: String,
    description: String,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(state.target.id, state.textTarget, state.mode, state.property, id) { mutableStateOf(initiallyExpanded) }
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(StudioPanel)
            .clickable(role = Role.Button) { focus.clearFocus(force = true); expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                StudioHint(description)
            }
            Icon(Icons.Default.ExpandMore, null, Modifier.rotate(if (expanded) 180f else 0f))
        }
        if (expanded) Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp), content = content)
    }
}
