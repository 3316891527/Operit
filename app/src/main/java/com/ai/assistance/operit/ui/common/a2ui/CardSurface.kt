package com.ai.assistance.operit.ui.common.a2ui

import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiComponentState
import androidx.a2ui.compose.runtime.LocalA2uiReadinessEvaluator
import androidx.a2ui.compose.runtime.observeA2uiComponentState
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.asReadinessEvaluator
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.engine.model.A2uiCoreSurfaceModel
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.a2ui.model.protocol.A2uiException
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** 使用当前聊天主题渲染官方引擎状态，不额外引入 Material A2UI 组件和图标。 */
@Composable
internal fun CardSurface(
    surface: A2uiSurfaceModel,
    modifier: Modifier,
    errorContent: @Composable (A2uiException) -> Unit,
) {
    require(surface is A2uiCoreSurfaceModel)
    val catalog = requireNotNull(surface.catalog as? A2uiCatalog)
    val readiness = remember(catalog) { catalog.asReadinessEvaluator() }
    CompositionLocalProvider(LocalA2uiReadinessEvaluator provides readiness) {
        val state = observeA2uiComponentState(surface)
        Box(modifier) {
            when (state) {
                is A2uiComponentState.Loading -> CircularProgressIndicator()
                is A2uiComponentState.Error -> errorContent(state.exception)
                is A2uiComponentState.Success -> CardVisualTheme { A2uiComponent(component = state.component) }
            }
        }
    }
}

/** 仅在卡片内统一字阶和圆角，沿用当前聊天主题的语义色。 */
@Composable
private fun CardVisualTheme(content: @Composable () -> Unit) {
    val typography = MaterialTheme.typography
    MaterialTheme(
        typography = typography.copy(
            titleLarge = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = typography.labelLarge.copy(fontWeight = FontWeight.Medium),
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = CardControlShape,
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}

internal object CardDivider : A2uiBasicCatalogV1.Divider {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        axis: A2uiBasicCatalogV1.Divider.Axis,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val description = accessibility?.label
        val accessible = if (description == null) modifier else modifier.semantics {
            contentDescription = description
        }
        val color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)
        when (axis) {
            A2uiBasicCatalogV1.Divider.Axis.Horizontal -> HorizontalDivider(accessible, color = color)
            A2uiBasicCatalogV1.Divider.Axis.Vertical -> VerticalDivider(accessible, color = color)
        }
    }
}
