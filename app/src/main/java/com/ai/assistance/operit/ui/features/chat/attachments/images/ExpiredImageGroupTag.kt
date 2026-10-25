package com.ai.assistance.operit.ui.features.chat.attachments.images

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.oktoast.OKToast

/** 同一条消息的过期图片合为一个标签，右上角显示数量，点击只提示原文件缺失。 */
@Composable
internal fun ExpiredImageGroupTag(count: Int, enabled: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val missingMessage = stringResource(R.string.chat_media_file_missing)
    Surface(
        modifier = modifier.widthIn(min = 156.dp, max = 280.dp).clickable(enabled = enabled, role = Role.Button) {
            OKToast.warning(context, missingMessage).show()
        },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.sent_image_expired_count, count),
                modifier = Modifier.align(Alignment.End),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.image), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
