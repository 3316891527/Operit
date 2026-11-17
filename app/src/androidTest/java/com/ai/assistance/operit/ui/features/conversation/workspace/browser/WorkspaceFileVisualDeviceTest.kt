package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.workspaceApkInstallIntent
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.workspaceReadApkIcon
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.workspaceReadApkInfo
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceFileVisualDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.cacheDir, "file-visual-test-${UUID.randomUUID()}").apply { check(mkdir()) }
    @After fun clean() { root.deleteRecursively() }

    @Test fun everyImportedVectorInflatesAndDrawsVisiblePixels() {
        val catalog = context.assets.open("workspace-file-icons/catalog.json").bufferedReader().use { JSONObject(it.readText()) }
        val icons = catalog.getJSONArray("icons")
        for (index in 0 until icons.length()) {
            val resource = icons.getJSONObject(index).getString("resource").substringAfter('/').removeSuffix(".xml")
            val id = context.resources.getIdentifier(resource, "drawable", context.packageName)
            assertNotEquals(resource, 0, id)
            val bitmap = checkNotNull(context.getDrawable(id)).toBitmap(64, 64)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue(resource, pixels.any { Color.alpha(it) > 0 })
        }
    }

    @Test fun imageThumbnailIsSampledCachedAndInvalidatedByTheFileVersion() = runBlocking<Unit> {
        val file = File(root, "photo.png")
        fun write(color: Int) {
            val bitmap = Bitmap.createBitmap(512, 384, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
        }
        val location = WorkspaceLocation(file.path)
        write(Color.RED)
        val red = WorkspaceThumbnailLoader.load(context, location, "red", WorkspaceThumbnailKind.IMAGE, 32, null)
        assertTrue(red.width <= 64 && red.height <= 64)
        assertEquals(Color.RED, red.getPixel(0, 0))
        write(Color.BLUE)
        assertSame(red, WorkspaceThumbnailLoader.load(context, location, "red", WorkspaceThumbnailKind.IMAGE, 32, null))
        val blue = WorkspaceThumbnailLoader.load(context, location, "blue", WorkspaceThumbnailKind.IMAGE, 32, null)
        assertEquals(Color.BLUE, blue.getPixel(0, 0))
    }

    @Test fun thumbnailPreparationDoesNotFollowASymbolicLink() = runBlocking<Unit> {
        val source = File(root, "source.png").apply { writeText("unread") }
        val link = File(root, "link.png")
        Files.createSymbolicLink(link.toPath(), source.toPath())
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            WorkspaceThumbnailLoader.prepareFile(context, WorkspaceLocation(link.path))
        } }
    }

    @Test fun apkInformationAndIconComeFromTheActualApplicationPackage() {
        val file = File(context.applicationInfo.sourceDir)
        val info = workspaceReadApkInfo(context, file)
        assertEquals(context.packageName, info.packageName)
        assertEquals(context.applicationInfo.minSdkVersion, info.minSdk)
        assertEquals(context.applicationInfo.targetSdkVersion, info.targetSdk)
        assertEquals(file.length(), info.size)
        assertNotNull(info.installedVersion)
        val icon = workspaceReadApkIcon(context, file, 48)
        assertEquals(48, icon.width)
        assertEquals(48, icon.height)
    }

    @Test fun installerReceivesAReadableContentUriWithExplicitReadPermission() {
        val file = File(root, "test.apk").apply { writeText("apk-content") }
        val intent = workspaceApkInstallIntent(context, file)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        val uri = checkNotNull(intent.data)
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertEquals("apk-content", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
    }
}