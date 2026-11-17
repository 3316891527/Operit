package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.motion

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.history.ChatListMotionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewChatIntroLifecycleAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val motion = ChatListMotionState()
    private var currentChatId by mutableStateOf<String?>("first")
    private var showIntro by mutableStateOf(true)
    private var introWasMeasured = false

    @Before
    fun startPlayingIntro() {
        assumeTrue("回归用例需要启用系统动画，确保进入真实绘制路径", ValueAnimator.areAnimatorsEnabled())
        composeRule.mainClock.autoAdvance = false
        motion.onChatCreated("first")
        composeRule.setContent {
            val requestId by motion.newChatId.collectAsState()
            MaterialTheme {
                Box(Modifier.size(320.dp, 480.dp)) {
                    if (showIntro) {
                        NewChatIntro(
                            requestId = requestId,
                            currentChatId = currentChatId,
                            ready = true,
                            empty = true,
                            hasDraft = false,
                            onConsumed = motion::consumeIntro,
                            modifier = Modifier.onSizeChanged { introWasMeasured = true },
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.runOnIdle {
            // 必须确认动画已经绘制，不能让尚未启动的动画掩盖空请求崩溃。
            assertTrue("入场动画应已进入布局和绘制", introWasMeasured)
            assertEquals("first", motion.newChatId.value)
        }
    }

    @Test
    fun clearingRequestWhilePlayingRemovesTheAnimationWithoutCrashing() {
        composeRule.runOnIdle { motion.consumeIntro("first") }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle { assertNull(motion.newChatId.value) }
    }

    @Test
    fun switchingAwayWhilePlayingConsumesThePreviousRequest() {
        composeRule.runOnIdle { currentChatId = "other" }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle { assertNull(motion.newChatId.value) }
    }

    @Test
    fun replacingRequestKeepsTheNewAnimationAliveUntilItsOwnCompletion() {
        composeRule.runOnIdle {
            introWasMeasured = false
            currentChatId = "second"
            motion.onChatCreated("second")
        }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.runOnIdle {
            assertTrue("新请求应创建自己的动画布局", introWasMeasured)
            assertEquals("旧动画的取消和离页回调不能消费新请求", "second", motion.newChatId.value)
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.runOnIdle { assertNull(motion.newChatId.value) }
    }

    @Test
    fun removingThePlayingComponentConsumesItsOwnRequest() {
        composeRule.runOnIdle { showIntro = false }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle { assertNull(motion.newChatId.value) }
    }
}
