package com.ai.assistance.operit.core.tools.javascript

import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QuickJsPendingJobPumpTest {
    private class Queue : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun next() { tasks.removeFirst().run() }
    }

    @Test
    fun `long chains finish in bounded batches without an external timer`() {
        val queue = Queue()
        var remaining = 1025
        val sizes = mutableListOf<Int>()
        val failures = mutableListOf<Throwable>()
        val pump = QuickJsPendingJobPump(queue, { false }, { budget ->
            val executed = minOf(remaining, budget)
            remaining -= executed
            sizes += executed
            executed
        }, failures::add)

        pump.drain()
        assertEquals(897, remaining)
        while (queue.tasks.isNotEmpty()) queue.next()
        assertEquals(0, remaining)
        assertEquals(listOf(128, 128, 128, 128, 128, 128, 128, 128, 1), sizes)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `continuations yield to queued timer and cancellation work`() {
        val queue = Queue()
        val order = mutableListOf<String>()
        var batches = 0
        val pump = QuickJsPendingJobPump(queue, { false }, { budget ->
            order += "batch-${++batches}"
            if (batches == 1) budget else 0
        }, { throw AssertionError(it) })
        queue.execute { order += "timer" }
        queue.execute { order += "cancel" }
        pump.drain()
        while (queue.tasks.isNotEmpty()) queue.next()
        assertEquals(listOf("batch-1", "timer", "cancel", "batch-2"), order)
    }

    @Test
    fun `new evaluations coalesce their queued continuation`() {
        val queue = Queue()
        var remaining = 600
        val pump = QuickJsPendingJobPump(queue, { false }, { budget ->
            minOf(remaining, budget).also { remaining -= it }
        }, { throw AssertionError(it) })
        repeat(3) { pump.drain() }
        assertEquals(1, queue.tasks.size)
        while (queue.tasks.isNotEmpty()) {
            assertEquals(1, queue.tasks.size)
            queue.next()
        }
        assertEquals(0, remaining)
    }

    @Test
    fun `an exact batch probes once more and an empty queue stays idle`() {
        val queue = Queue()
        var remaining = 128
        var calls = 0
        val pump = QuickJsPendingJobPump(queue, { false }, { budget ->
            calls++
            minOf(remaining, budget).also { remaining -= it }
        }, { throw AssertionError(it) })
        pump.drain()
        assertEquals(1, queue.tasks.size)
        queue.next()
        assertEquals(2, calls)
        assertTrue(queue.tasks.isEmpty())
    }

    @Test
    fun `closing before a queued continuation prevents all further native work`() {
        val queue = Queue()
        var closed = false
        var calls = 0
        val pump = QuickJsPendingJobPump(queue, { closed }, { budget -> calls++; budget }, { throw AssertionError(it) })
        pump.drain()
        closed = true
        queue.next()
        pump.drain()
        assertEquals(1, calls)
        assertTrue(queue.tasks.isEmpty())
    }

    @Test
    fun `closing during a batch prevents continuation enqueue`() {
        val queue = Queue()
        var closed = false
        val pump = QuickJsPendingJobPump(queue, { closed }, { budget -> closed = true; budget }, { throw AssertionError(it) })
        pump.drain()
        assertTrue(queue.tasks.isEmpty())
    }

    @Test
    fun `a failed queued batch reports once and stops rescheduling`() {
        val queue = Queue()
        val failure = IllegalStateException("合成执行错误")
        val failures = mutableListOf<Throwable>()
        var first = true
        val pump = QuickJsPendingJobPump(queue, { false }, { budget ->
            if (!first) throw failure
            first = false
            budget
        }, failures::add)
        pump.drain()
        queue.next()
        assertEquals(1, failures.size)
        assertSame(failure, failures.single())
        assertTrue(queue.tasks.isEmpty())
    }

    @Test
    fun `executor rejection only disappears when the engine is closing`() {
        var closed = false
        var rejected = true
        val queue = Queue()
        val executor = Executor { command ->
            if (rejected) throw RejectedExecutionException("合成退役队列")
            queue.execute(command)
        }
        val pump = QuickJsPendingJobPump(executor, { closed }, { it }, { throw AssertionError(it) })
        try {
            pump.drain()
            fail("活动引擎的调度失败必须向调用方报告")
        } catch (_: RejectedExecutionException) { }
        rejected = false
        pump.drain()
        assertEquals(1, queue.tasks.size)
        closed = true
        queue.next()
        assertTrue(queue.tasks.isEmpty())

        var closing = false
        val retiring = QuickJsPendingJobPump(Executor { closing = true; throw RejectedExecutionException() }, { closing }, { it }, { throw AssertionError(it) })
        retiring.drain()
        assertTrue(closing)
    }
}