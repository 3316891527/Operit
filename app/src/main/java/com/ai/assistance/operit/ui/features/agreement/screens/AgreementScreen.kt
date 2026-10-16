package com.ai.assistance.operit.ui.features.agreement.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.AgreementPreferences
import kotlinx.coroutines.delay

@Composable
fun AgreementScreen(onAgreementAccepted: () -> Unit, showAcceptanceButton: Boolean = true) {
        val scrollState = rememberScrollState()
        var isButtonEnabled by remember { mutableStateOf(false) }
        var remainingSeconds by remember { mutableStateOf(5) }

        // 阅读入口无需等待同意倒计时，首次启动仍按原流程确认。
        LaunchedEffect(showAcceptanceButton) {
                if (showAcceptanceButton) {
                        repeat(5) {
                                delay(1000)
                                remainingSeconds--
                        }
                        isButtonEnabled = true
                }
        }

        // 软件内阅读时整页滚动；首次启动仍保留独立的底部确认区。
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .then(
                                        if (showAcceptanceButton) Modifier
                                        else Modifier.verticalScroll(scrollState)
                                )
                                .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                        text = stringResource(R.string.agreement_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (showAcceptanceButton) {
                        Text(
                                text = stringResource(R.string.agreement_subtitle),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                        text = stringResource(
                                R.string.agreement_version,
                                AgreementPreferences.CURRENT_AGREEMENT_VERSION
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 协议正文
                Box(
                        modifier =
                                (if (showAcceptanceButton) Modifier.weight(1f) else Modifier)
                                        .fillMaxWidth()
                                        .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                shape = MaterialTheme.shapes.medium
                                        )
                                        .padding(16.dp)
                ) {
                        Column(
                                modifier =
                                        if (showAcceptanceButton) {
                                                Modifier.fillMaxSize().verticalScroll(scrollState)
                                        } else {
                                                Modifier.fillMaxWidth()
                                        }
                        ) {
                                // 简明说明
                                Text(
                                        text = stringResource(R.string.agreement_human_readable_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                        text = stringResource(R.string.agreement_human_readable_content),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(24.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(24.dp))

                                // 完整协议
                                Text(
                                        text = stringResource(R.string.agreement_serious_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                val agreementHtml = stringResource(R.string.agreement_serious_content)
                                val agreementText = remember(agreementHtml) {
                                        AnnotatedString.fromHtml(agreementHtml)
                                }
                                // 统一使用 Compose 排版，避免原生视图的颜色和尺寸脱离当前主题。
                                Text(
                                        text = agreementText,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(24.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(24.dp))

                                // 说明与免责声明
                                Text(
                                        text = stringResource(R.string.agreement_disclaimer),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                        }
                }

                if (showAcceptanceButton) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onAgreementAccepted,
                        enabled = isButtonEnabled,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor =
                                                MaterialTheme.colorScheme.primary.copy(
                                                        alpha = 0.5f
                                                ),
                                        disabledContentColor =
                                                MaterialTheme.colorScheme.onPrimary.copy(
                                                        alpha = 0.7f
                                                )
                                )
                    ) {
                        Text(
                                text =
                                        if (isButtonEnabled)
                                            stringResource(R.string.agreement_accept)
                                        else
                                            stringResource(R.string.agreement_wait, remainingSeconds),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
        }
}
