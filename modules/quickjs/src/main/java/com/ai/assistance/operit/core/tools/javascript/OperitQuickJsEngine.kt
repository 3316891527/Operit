package com.ai.assistance.operit.core.tools.javascript

import java.io.Closeable
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class OperitQuickJsEngine : Closeable {

    private val runtimeRef = AtomicReference<QuickJsNativeRuntime?>()
    private val runtimeThread = AtomicReference<Thread?>()
    private val nativeInterfaceRef = AtomicReference<Any?>()
    private val methodCache = ConcurrentHashMap<String, Method>()
    private val closed = AtomicBoolean(false)
    // Java Thread.id 是 JVM 内计数，不是 /proc/self/task 里的内核 tid，
    // 所以只能在运行线程自己启动时记录 Process.myTid() 供外部按线程采样 CPU。
    private val runtimeTid = AtomicLong(-1L)
    private val runtimeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            runtimeTid.set(android.os.Process.myTid().toLong())
            runnable.run()
        }, "OperitQuickJsRuntime").apply {
            isDaemon = true
            runtimeThread.set(this)
        }
    }
    private val hostDispatcher = QuickJsNativeHostDispatcher(
        dispatchTimer = ::dispatchTimerOnRuntimeThread,
        forwardCall = ::dispatchNativeCall
    )
    private val runtime = runOnRuntimeThread {
        QuickJsNativeRuntime.create(hostDispatcher).also { quickJs ->
            runtimeRef.set(quickJs)
            quickJs.installCompatLayerOrThrow()
        }
    }

    private val pendingJobs = QuickJsPendingJobPump(
        executor = runtimeExecutor,
        isClosed = closed::get,
        executeJobs = { budget -> runtime.executePendingJobs(budget) },
        reportFailure = { failure ->
            System.err.println("QuickJS pending job dispatch failed: ${failure.message}")
            failure.printStackTrace()
        }
    )

    fun bindNativeInterface(instance: Any) {
        check(!closed.get()) { "QuickJS engine already closed" }
        nativeInterfaceRef.set(instance)
        methodCache.clear()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> evaluate(script: String, fileName: String = "<eval>"): T? {
        check(!closed.get()) { "QuickJS engine already closed" }
        return runOnRuntimeThread {
            check(!closed.get()) { "QuickJS engine already closed" }
            val result = runtime.eval(script, fileName)
            pendingJobs.drain()
            if (!result.success) {
                error(result.describeFailure("QuickJS evaluation failed"))
            }
            decodeJsonValue(result.valueJson) as T?
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> callFunction(
        functionName: String,
        argsJson: String,
        callSite: String = "<call:$functionName>"
    ): T? {
        check(!closed.get()) { "QuickJS engine already closed" }
        return runOnRuntimeThread {
            check(!closed.get()) { "QuickJS engine already closed" }
            val result = runtime.callFunction(functionName, argsJson, callSite)
            pendingJobs.drain()
            if (!result.success) {
                error(result.describeFailure("QuickJS function call failed"))
            }
            decodeJsonValue(result.valueJson) as T?
        }
    }

    fun interrupt() {
        runtimeRef.get()?.interrupt()
    }

    /** 运行线程的内核 tid；线程尚未启动或已关闭时返回 -1。 */
    fun getRuntimeTid(): Long {
        return runtimeTid.get()
    }

    fun getMemoryUsage(): QuickJsMemoryUsage {
        check(!closed.get()) { "QuickJS engine already closed" }
        return runOnRuntimeThread {
            check(!closed.get()) { "QuickJS engine already closed" }
            runtime.getMemoryUsage()
        }
    }

    fun resetMemoryPeak() {
        check(!closed.get()) { "QuickJS engine already closed" }
        runOnRuntimeThread {
            check(!closed.get()) { "QuickJS engine already closed" }
            runtime.resetMemoryPeak()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // 先发布关闭状态并中断脚本，防止队列中的调用继续进入原生运行时。
        try {
            interrupt()
        } finally {
            try {
                hostDispatcher.close()
            } finally {
                val cleanup = try {
                    // 即使由原生桥回调发起关闭，也要等当前 JNI 调用退栈后再销毁。
                    runtimeExecutor.submit {
                        try {
                            try {
                                runtime.clearAllTimers()
                            } finally {
                                runtime.close()
                            }
                        } finally {
                            runtimeTid.set(-1L)
                            runtimeRef.set(null)
                            nativeInterfaceRef.set(null)
                            methodCache.clear()
                        }
                    }
                } finally {
                    // 已接受的同步调用必须得到执行并报告关闭，不能丢弃它们等待的 Future。
                    runtimeExecutor.shutdown()
                }
                if (Thread.currentThread() !== runtimeThread.get()) {
                    awaitRuntimeCleanup(cleanup)
                }
            }
        }
    }

    private fun awaitRuntimeCleanup(cleanup: Future<*>) {
        var interrupted = false
        try {
            while (true) {
                try {
                    cleanup.get()
                    break
                } catch (_: InterruptedException) {
                    // 关闭必须等原生清理结束；之后恢复调用线程的中断状态。
                    interrupted = true
                } catch (failure: ExecutionException) {
                    throw (failure.cause ?: failure)
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun dispatchTimerOnRuntimeThread(timerId: Int) {
        if (closed.get()) {
            return
        }
        try {
            runtimeExecutor.execute {
                if (closed.get()) {
                    return@execute
                }
                runCatching {
                    val result = runtime.dispatchTimer(timerId)
                    pendingJobs.drain()
                    if (!result.success) {
                        error(result.describeFailure("QuickJS timer callback failed"))
                    }
                }.getOrElse { error ->
                    System.err.println("QuickJS timer dispatch failed: ${error.message}")
                    error.printStackTrace()
                }
            }
        } catch (error: RejectedExecutionException) {
            if (!closed.get()) {
                throw error
            }
        }
    }

    private fun <T> runOnRuntimeThread(block: () -> T): T {
        if (Thread.currentThread() === runtimeThread.get()) {
            return block()
        }
        val future = runtimeExecutor.submit<T> { block() }
        try {
            return future.get()
        } catch (error: ExecutionException) {
            throw (error.cause ?: error)
        }
    }

    private fun dispatchNativeCall(methodName: String, argsJson: String?): String? {
        val target = nativeInterfaceRef.get() ?: error("NativeInterface is not bound")
        val args = decodeArgs(argsJson)
        val method = resolveMethod(target, methodName, args.size)
        val convertedArgs = method.parameterTypes.mapIndexed { index, type ->
            convertArg(args[index], type)
        }.toTypedArray()
        return method.invoke(target, *convertedArgs)?.toString()
    }

    private fun resolveMethod(target: Any, methodName: String, argCount: Int): Method {
        val cacheKey = "${target.javaClass.name}#$methodName/$argCount"
        return methodCache.getOrPut(cacheKey) {
            target.javaClass.methods.firstOrNull { method ->
                method.name == methodName && method.parameterTypes.size == argCount
            } ?: error("NativeInterface method not found: $methodName/$argCount")
        }
    }

    private fun decodeArgs(argsJson: String?): List<Any?> {
        if (argsJson.isNullOrBlank()) {
            return emptyList()
        }
        val parsed = JSONTokener(argsJson).nextValue()
        if (parsed !is JSONArray) {
            return emptyList()
        }
        return List(parsed.length()) { index -> normalizeJsonValue(parsed.opt(index)) }
    }

    private fun decodeJsonValue(valueJson: String?): Any? {
        if (valueJson.isNullOrBlank()) {
            return null
        }
        return normalizeJsonValue(JSONTokener(valueJson).nextValue())
    }

    private fun normalizeJsonValue(value: Any?): Any? {
        return when (value) {
            JSONObject.NULL -> null
            is JSONArray -> List(value.length()) { index -> normalizeJsonValue(value.opt(index)) }
            is JSONObject -> value.toString()
            else -> value
        }
    }

    private fun convertArg(value: Any?, parameterType: Class<*>): Any? {
        return when (parameterType) {
            java.lang.String::class.java -> value?.toString() ?: ""
            java.lang.Integer.TYPE,
            java.lang.Integer::class.java -> (value as? Number)?.toInt()
                ?: value?.toString()?.toIntOrNull()
                ?: 0
            java.lang.Long.TYPE,
            java.lang.Long::class.java -> (value as? Number)?.toLong()
                ?: value?.toString()?.toLongOrNull()
                ?: 0L
            java.lang.Boolean.TYPE,
            java.lang.Boolean::class.java -> when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                else -> value?.toString()?.toBooleanStrictOrNull() ?: false
            }
            java.lang.Double.TYPE,
            java.lang.Double::class.java -> (value as? Number)?.toDouble()
                ?: value?.toString()?.toDoubleOrNull()
                ?: 0.0
            java.lang.Float.TYPE,
            java.lang.Float::class.java -> (value as? Number)?.toFloat()
                ?: value?.toString()?.toFloatOrNull()
                ?: 0f
            else -> value?.toString()
        }
    }
}
