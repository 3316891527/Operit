package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.util.AppLogger
import java.net.URL
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

/**
 * DeepSeek Files API 图片上传器，供 Chat Completions 与 Responses 两种协议共用。
 *
 * 职责：发送请求前把消息里的图片上传至 DeepSeek Files API 换取 file_id，
 * 并按图片 id 做会话级缓存，避免同一图片重复上传。
 * 上传失败的图片不缓存，由调用方回退为 base64 内嵌。
 */
internal class DeepseekFileUploader(
    private val apiEndpoint: String,
    private val apiKeyProvider: ApiKeyProvider,
    private val httpClient: OkHttpClient,
    private val customHeaders: Map<String, String>
) {
    /** 图片 id -> DeepSeek Files API file_id 的会话级缓存（避免同一图片重复上传） */
    private val uploadedImageFileIds = ConcurrentHashMap<String, String>()
    /** DeepSeek Files API 支持上传的图片 MIME 类型 */
    private val supportedUploadMimeTypes = setOf("image/jpeg", "image/png", "image/gif", "image/webp")
    /** DeepSeek Files API 单文件上传上限（64 MiB） */
    private val maxUploadBytes = 64L * 1024L * 1024L

    /**
     * 扫描消息历史中的图片并上传至 DeepSeek Files API 换取 file_id。
     * 上传失败的图片保持原样，构建请求时回退为 base64 内嵌（见各 Provider 的 buildImageContentPart）。
     * @param supportsVision 当前配置是否开启"直接图片处理"；关闭时不触发上传
     * @param logTag 日志标签（区分 Chat Completions / Responses 两条调用线）
     */
    suspend fun prepareMedia(supportsVision: Boolean, chatHistory: List<PromptTurn>, logTag: String) {
        if (!supportsVision) return
        val seenIds = mutableSetOf<String>()
        val imageLinks = mutableListOf<ImageLink>()
        for (turn in chatHistory) {
            MediaLinkParser.extractImageLinks(turn.content).forEach { link ->
                if (seenIds.add(link.id)) imageLinks.add(link)
            }
        }
        for (link in imageLinks) {
            if (uploadedImageFileIds.containsKey(link.id)) continue
            if (link.mimeType.lowercase() !in supportedUploadMimeTypes) continue
            val bytes =
                runCatching { Base64.getDecoder().decode(link.base64Data) }.getOrNull() ?: continue
            if (bytes.isEmpty() || bytes.size > maxUploadBytes) continue
            val fileId = uploadImageToFilesApi(bytes, link.mimeType, link.id)
            if (fileId != null) {
                uploadedImageFileIds[link.id] = fileId
                AppLogger.d(logTag, "图片 ${link.id} 已上传至 DeepSeek Files API: $fileId")
            }
        }
    }

    /** 已上传图片对应的 file_id；未上传返回 null（调用方回退 base64） */
    fun fileIdFor(linkId: String): String? = uploadedImageFileIds[linkId]

    /** 上传单张图片到 DeepSeek Files API，返回 file_id；失败返回 null */
    private suspend fun uploadImageToFilesApi(
        bytes: ByteArray,
        mimeType: String,
        linkId: String
    ): String? {
        val filesEndpoint = buildFilesEndpoint() ?: return null
        return runCatching {
            val body =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("purpose", "user_data")
                    .addFormDataPart(
                        "file",
                        "image_${linkId.take(24)}.${extensionForMime(mimeType)}",
                        RequestBody.create(mimeType.toMediaType(), bytes)
                    )
                    .build()
            val builder = Request.Builder().url(filesEndpoint).post(body)
            val apiKey = apiKeyProvider.getApiKey().trim()
            if (apiKey.isNotEmpty()) {
                builder.addHeader("Authorization", "Bearer $apiKey")
            }
            customHeaders.forEach { (key, value) ->
                if (!key.equals("Content-Type", ignoreCase = true)) builder.addHeader(key, value)
            }
            withContext(Dispatchers.IO) { httpClient.newCall(builder.build()).execute() }.use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(
                        "DeepseekFileUploader",
                        "Files API 上传失败 HTTP ${response.code}: ${response.body?.string()?.take(200)}"
                    )
                    return@use null
                }
                val json = JSONObject(response.body?.string().orEmpty())
                json.optString("id").takeIf { it.startsWith("file-") }
            }
        }.getOrElse { e ->
            AppLogger.w("DeepseekFileUploader", "Files API 上传异常: ${e.message}")
            null
        }
    }

    /** 由聊天 API 端点推导 Files API 上传地址（官方为 https://api.deepseek.com/files） */
    private fun buildFilesEndpoint(): String? {
        val raw = apiEndpoint.trim().removeSuffix("#").trim()
        return runCatching {
            val url = URL(raw)
            val portPart = if (url.port in 1..65535) ":" + url.port else ""
            "${url.protocol}://${url.host}${portPart}/files"
        }.getOrNull()
    }

    private fun extensionForMime(mimeType: String): String {
        return when (mimeType.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> "bin"
        }
    }
}