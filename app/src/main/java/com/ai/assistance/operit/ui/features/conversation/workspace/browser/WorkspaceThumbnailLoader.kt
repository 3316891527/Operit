package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.ai.assistance.operit.ui.common.icons.SvgImageAssetDecoder
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.workspaceReadApkIcon
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties.*
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** 只为可见行准备缩略图；两项并发限制解析、传输和解码，复用 Coil 内存缓存。 */
internal object WorkspaceThumbnailLoader {
    private val slots = Semaphore(2)
    private val cacheLock = Mutex()
    private var cachePrepared = false

    suspend fun load(context: Context, location: WorkspaceLocation, version: String, kind: WorkspaceThumbnailKind,
        pixels: Int, archiveFile: (suspend () -> File)?): Bitmap = withContext(Dispatchers.IO) {
        val key = "workspace-thumbnail:${location.key}:$version:$pixels"
        val loader = context.imageLoader
        val cached = loader.memoryCache?.get(MemoryCache.Key(key))
        if (cached != null) return@withContext cached.bitmap
        slots.withPermit {
            currentCoroutineContext().ensureActive()
            val data: Any = if (kind == WorkspaceThumbnailKind.APK) {
                val file = if (archiveFile == null) prepareFile(context, location) else archiveFile()
                workspaceReadApkIcon(context, file, pixels)
            } else if (archiveFile != null) archiveFile()
            else if (location.environment.startsWith("repo:")) {
                val source = SafWorkspacePropertySource.open(context, WorkspacePropertiesTarget(location.path, location.environment))
                val node = source.stat(source.root)
                require(!node.directory && !node.symbolicLink)
                Uri.parse(source.root)
            } else prepareFile(context, location)
            val request = ImageRequest.Builder(context).data(data).size(pixels, pixels)
                .memoryCacheKey(key).diskCachePolicy(CachePolicy.DISABLED).allowHardware(false)
                .apply {
                    if (location.path.substringAfterLast('.').equals("svg", true)) decoderFactory(SvgImageAssetDecoder.Factory(pixels))
                }.build()
            when (val result = loader.execute(request)) {
                is SuccessResult -> result.drawable.toBitmap()
                is ErrorResult -> throw result.throwable
            }
        }
    }

    suspend fun prepareFile(context: Context, location: WorkspaceLocation): File = withContext(Dispatchers.IO) {
        val operations = FileBrowserOperations(context)
        val local = operations.localFile(location.path, location.environment)
        if (local != null) {
            WorkspacePropertyAccess(context).requireReadable(local)
            require(Files.isRegularFile(local.toPath(), LinkOption.NOFOLLOW_LINKS))
            return@withContext local
        }
        val properties = WorkspacePropertiesRepository.open(context, WorkspacePropertiesTarget(location.path, location.environment))
        val before = properties.read().node
        require(!before.directory && !before.symbolicLink)
        cacheLock.withLock {
            val cache = File(context.cacheDir, "workspace_thumbnails").also { check(it.isDirectory || it.mkdirs()) }
            if (!cachePrepared) { pruneWorkspaceShareCache(cache); cachePrepared = true }
            val key = MessageDigest.getInstance("SHA-256").digest((location.key + before).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val directory = File(cache, key)
            val file = File(directory, location.path.substringAfterLast('/'))
            if (Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) return@withLock file
            if (directory.exists()) check(directory.deleteRecursively())
            val pending = File(cache, "pending-${UUID.randomUUID()}").also { check(it.mkdir()) }
            try {
                operations.transfer(listOf(FileBrowserClipboardItem(location.path, location.environment, false, false)), pending.path, "android")
                check(properties.read().node == before) { "File source changed" }
                currentCoroutineContext().ensureActive()
                Files.move(pending.toPath(), directory.toPath())
                file
            } finally { pending.deleteRecursively() }
        }
    }
}