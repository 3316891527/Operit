package com.ai.assistance.operit.core.tools.javascript

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** 使用真实 JNI 检查 Promise 预算续排，不能靠再次求值或轮询去推进待处理任务。 */
@RunWith(AndroidJUnit4::class)
class OperitQuickJsSchedulingTest {
    class Probe {
        val completed = CountDownLatch(1)
        val started = CountDownLatch(1)
        val value = AtomicReference<String>()
        val completions = AtomicInteger()

        fun completed(result: String): String {
            value.set(result)
            completions.incrementAndGet()
            completed.countDown()
            return ""
        }

        fun started(): String {
            started.countDown()
            return ""
        }
    }

    class ClosingProbe(private val engine: OperitQuickJsEngine) {
        fun closeEngine(): String {
            engine.close()
            return "close-requested"
        }
    }

    private fun expectCompletion(probe: Probe, expected: String) {
        assertTrue("没有外部计时唤醒时，长 Promise 链必须自行完成", probe.completed.await(3, TimeUnit.SECONDS))
        assertEquals(expected, probe.value.get())
        assertEquals(1, probe.completions.get())
    }

    @Test(timeout = 10000)
    fun evaluationDrainsLongAwaitChainWithoutTimers() {
        OperitQuickJsEngine().use { engine ->
            val probe = Probe()
            engine.bindNativeInterface(probe)
            engine.evaluate<Any?>("""
                (async function() {
                    var total = 0;
                    for (var i = 0; i < 2048; i++) total += await Promise.resolve(1);
                    NativeInterface.completed(String(total));
                })();
            """.trimIndent())
            expectCompletion(probe, "2048")
        }
    }

    @Test(timeout = 10000)
    fun functionCallDrainsLongThenChainWithoutAnotherCall() {
        OperitQuickJsEngine().use { engine ->
            val probe = Probe()
            engine.bindNativeInterface(probe)
            engine.evaluate<Any?>("""
                globalThis.startLongChain = function() {
                    var chain = Promise.resolve(0);
                    for (var i = 0; i < 2048; i++) chain = chain.then(function(n) { return n + 1; });
                    chain.then(function(n) { NativeInterface.completed(String(n)); });
                };
            """.trimIndent())
            engine.callFunction<Any?>("startLongChain", "[]")
            expectCompletion(probe, "2048")
        }
    }

    @Test(timeout = 10000)
    fun timerCallbackDrainsItsLongPromiseChain() {
        OperitQuickJsEngine().use { engine ->
            val probe = Probe()
            engine.bindNativeInterface(probe)
            engine.evaluate<Any?>("""
                setTimeout(async function() {
                    for (var i = 0; i < 2048; i++) await Promise.resolve();
                    NativeInterface.completed('timer-done');
                }, 0);
            """.trimIndent())
            expectCompletion(probe, "timer-done")
        }
    }

    @Test(timeout = 10000)
    fun rejectionHandlersBeyondTheFirstBatchStillRun() {
        OperitQuickJsEngine().use { engine ->
            val probe = Probe()
            engine.bindNativeInterface(probe)
            engine.evaluate<Any?>("""
                var chain = Promise.resolve();
                for (var i = 0; i < 1024; i++) chain = chain.then(function() {});
                chain.then(function() { throw new Error('合成拒绝'); })
                    .catch(function() { NativeInterface.completed('caught'); });
            """.trimIndent())
            expectCompletion(probe, "caught")
        }
    }

    @Test(timeout = 10000)
    fun interruptedCloseStillWaitsForNativeCleanupAndRestoresInterrupt() {
        val engine = OperitQuickJsEngine()
        try {
            engine.evaluate<Any?>("setInterval(function() {}, 5);")
            Thread.currentThread().interrupt()
            engine.close()
            assertTrue("关闭完成后必须保留调用方的中断状态", Thread.interrupted())
            assertEquals(-1L, engine.getRuntimeTid())
        } finally {
            Thread.interrupted()
            engine.close()
        }
    }

    @Test(timeout = 10000)
    fun closeFromNativeCallbackDefersDestructionUntilTheCallbackReturns() {
        val existing = Thread.getAllStackTraces().keys.map { it.id }.toSet()
        val engine = OperitQuickJsEngine()
        try {
            engine.bindNativeInterface(ClosingProbe(engine))
            val runtime = Thread.getAllStackTraces().keys.single {
                it.id !in existing && it.name == "OperitQuickJsRuntime"
            }
            assertEquals("close-requested", engine.evaluate<String>("NativeInterface.closeEngine();"))
            runtime.join(3000)
            assertFalse("原生回调退栈后必须完成引擎销毁", runtime.isAlive)
            assertEquals(-1L, engine.getRuntimeTid())
        } finally {
            engine.close()
        }
    }

    @Test(timeout = 15000)
    fun closeStopsSelfReplenishingJobsAndTimersAndIsIdempotent() {
        val existing = Thread.getAllStackTraces().keys.map { it.id }.toSet()
        val engine = OperitQuickJsEngine()
        val probe = Probe()
        try {
            engine.bindNativeInterface(probe)
            engine.evaluate<Any?>("""
                setInterval(function() {}, 5);
                NativeInterface.started();
                function again() { Promise.resolve().then(again); }
                again();
            """.trimIndent())
            assertTrue(probe.started.await(3, TimeUnit.SECONDS))
            val owned = Thread.getAllStackTraces().keys.filter {
                it.id !in existing && it.name in setOf("OperitQuickJsRuntime", "QuickJsNativeTimer")
            }
            assertTrue("必须实际创建运行和计时线程", owned.size >= 2)
            engine.close()
            engine.close()
            owned.forEach { thread ->
                thread.join(3000)
                assertFalse("关闭后仍有运行线程：${thread.name}", thread.isAlive)
            }
            assertEquals(-1L, engine.getRuntimeTid())
            try {
                engine.evaluate<Any?>("42")
                fail("已经关闭的引擎不能继续求值")
            } catch (_: IllegalStateException) { }
        } finally {
            engine.close()
        }
    }
}