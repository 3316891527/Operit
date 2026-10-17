package com.ai.assistance.operit.ui.features.agreement.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.AgreementPreferences

@Composable
fun AgreementScreen() {
    val context = LocalContext.current
    // 直接读取用户提供的完整草案，避免把旧资源误当作当前协议。
    val agreementMarkdown = remember(context) {
        context.resources
            .openRawResource(R.raw.operit_user_agreement_v02)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }
    val agreementText = remember(agreementMarkdown) {
        agreementMarkdownToAnnotatedString(agreementMarkdown)
    }

    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.agreement_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(
                    R.string.agreement_version,
                    AgreementPreferences.CURRENT_AGREEMENT_VERSION
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = agreementText,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

private fun agreementMarkdownToAnnotatedString(markdown: String): AnnotatedString {
    val lines = markdown.lines()
    return buildAnnotatedString {
        lines.forEachIndexed { index, line ->
            val visibleLine = line.trimEnd()
            when {
                visibleLine.startsWith("# ") -> {
                    withStyle(
                        SpanStyle(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(visibleLine.removePrefix("# ").trim())
                    }
                }
                visibleLine.startsWith("## ") -> {
                    withStyle(
                        SpanStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(visibleLine.removePrefix("## ").trim())
                    }
                }
                else -> append(visibleLine)
            }
            if (index < lines.lastIndex) {
                append('\n')
            }
        }
    }
}
