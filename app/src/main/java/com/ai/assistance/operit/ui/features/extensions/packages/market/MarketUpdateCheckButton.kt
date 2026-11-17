package com.ai.assistance.operit.ui.features.extensions.packages.market

import com.ai.assistance.operit.ui.common.oktoast.OKToast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketV2Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 市场检查更新按钮；简单结果使用提示卡片，有更新时保留详情跳转确认。
 * @param markerProvider 读取当前安装项市场安装标记的挂起函数；返回 null 视为非市场来源
 * @param onOpenMarketDetail 有更新时点击"前往更新"跳转市场详情页
 */
@Composable
fun MarketUpdateCheckButton(
    markerProvider: suspend () -> MarketInstallMarker?,
    onOpenMarketDetail: (MarketV2Entry) -> Unit,
    modifier: Modifier = Modifier
) {
    var isChecking by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var availableUpdate by remember { mutableStateOf<MarketUpdateCheckResult.UpdateAvailable?>(null) }
    val scope = rememberCoroutineScope()

    OutlinedButton(
        onClick = {
            if (isChecking) return@OutlinedButton
            isChecking = true
            availableUpdate = null
            scope.launch {
                val marker =
                    try {
                        withContext(Dispatchers.IO) { markerProvider() }
                    } catch (e: Exception) {
                        null
                    }
                when (val result = checkMarketUpdate(marker)) {
                    is MarketUpdateCheckResult.UpdateAvailable -> availableUpdate = result
                    is MarketUpdateCheckResult.UpToDate ->
                        OKToast.success(context, R.string.pkg_check_update_up_to_date).show()
                    MarketUpdateCheckResult.NotFromMarket ->
                        OKToast.info(context, R.string.pkg_check_update_not_market).show()
                    MarketUpdateCheckResult.EntryMissing ->
                        OKToast.warning(context, R.string.pkg_check_update_entry_missing).show()
                    MarketUpdateCheckResult.Failed ->
                        OKToast.error(context, R.string.pkg_check_update_failed, OKToast.LENGTH_LONG).show()
                }
                isChecking = false
            }
        },
        modifier = modifier,
        enabled = !isChecking
    ) {
        if (isChecking) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(stringResource(R.string.pkg_check_update))
    }

    availableUpdate?.let { current ->
        AlertDialog(
            onDismissRequest = { availableUpdate = null },
            title = { Text(stringResource(R.string.pkg_check_update)) },
            text = { Text(stringResource(R.string.pkg_check_update_available, current.latestVersion)) },
            confirmButton = {
                TextButton(onClick = {
                    availableUpdate = null
                    onOpenMarketDetail(current.entry)
                }) {
                    Text(stringResource(R.string.pkg_check_update_go))
                }
            },
            dismissButton = {
                TextButton(onClick = { availableUpdate = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
