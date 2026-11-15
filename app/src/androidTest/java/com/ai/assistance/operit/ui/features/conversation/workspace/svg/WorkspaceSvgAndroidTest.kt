package com.ai.assistance.operit.ui.features.conversation.workspace.svg

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Xml
import androidx.core.graphics.PathParser
import androidx.core.graphics.drawable.toBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ai.assistance.operit.ui.common.icons.ImageAssetLoader
import com.ai.assistance.operit.ui.common.icons.SvgImageAssetDecoder
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceLocation
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceThumbnailKind
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceThumbnailLoader
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.workspaceThumbnailKind
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.WorkspaceFileRoute
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.workspaceFileRoute
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.workspaceMimeTypeForPath
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class WorkspaceSvgAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.cacheDir, "workspace-svg-test-${UUID.randomUUID()}").apply { check(mkdir()) }
    private val svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="4 8 20 10">
          <rect x="6" y="10" width="6" height="6" fill="#f00"/>
          <rect x="16" y="10" width="6" height="6" fill="#00f"/>
        </svg>
    """.trimIndent().toByteArray(Charsets.UTF_8)

    @After fun clean() { root.deleteRecursively() }

    @Test fun fittedSvgKeepsNaturalAspectRatioTransparencyAndOriginalColors() {
        val asset = svg.inputStream().use(ImageAssetLoader::readSvg)
        assertEquals(20f, asset.width, 0f)
        assertEquals(10f, asset.height, 0f)
        val bitmap = asset.renderFitted(200)
        try {
            assertEquals(200, bitmap.width)
            assertEquals(100, bitmap.height)
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            assertEquals(Color.RED, bitmap.getPixel(50, 50))
            assertEquals(Color.BLUE, bitmap.getPixel(150, 50))
        } finally { bitmap.recycle() }
    }

    @Test fun actualCoilPreviewRequestDecodesSvgInsteadOfReportingAnImageError() = runBlocking<Unit> {
        val file = File(root, "colors.SVG").apply { writeBytes(svg) }
        val result = context.imageLoader.execute(ImageRequest.Builder(context).data(file)
            .decoderFactory(SvgImageAssetDecoder.Factory(200)).allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build())
        if (result is ErrorResult) throw AssertionError("SVG preview failed", result.throwable)
        assertTrue(result is SuccessResult)
        val bitmap = (result as SuccessResult).drawable.toBitmap()
        assertEquals(200, bitmap.width)
        assertEquals(100, bitmap.height)
        assertEquals(Color.RED, bitmap.getPixel(50, 50))
        assertEquals(Color.BLUE, bitmap.getPixel(150, 50))
    }

    @Test fun svgRoutesDirectlyToPreviewAndLoadsAsAProportionalThumbnail() = runBlocking<Unit> {
        val file = File(root, "colors.SVG").apply { writeBytes(svg) }
        assertEquals("image/svg+xml", workspaceMimeTypeForPath(file.path))
        assertEquals(WorkspaceFileRoute.PREVIEW, workspaceFileRoute(file.path, workspaceMimeTypeForPath(file.path)))
        assertEquals(WorkspaceThumbnailKind.IMAGE, workspaceThumbnailKind(file.path))
        val bitmap = WorkspaceThumbnailLoader.load(context, WorkspaceLocation(file.path, "android"), "svg", WorkspaceThumbnailKind.IMAGE, 100, null)
        assertEquals(100, bitmap.width)
        assertEquals(50, bitmap.height)
        assertEquals(Color.RED, bitmap.getPixel(25, 25))
        assertEquals(Color.BLUE, bitmap.getPixel(75, 25))
    }

    @Test fun pngExportWritesTheChosenDimensionsAndPreservesTransparentPixels() = runBlocking<Unit> {
        val source = File(root, "source.svg").apply { writeBytes(svg) }
        val output = WorkspaceSvgExport.export(context, svg, WorkspaceSvgFormat.PNG, "converted", WorkspaceLocation(root.path, "android"), 400, 200)
        assertEquals(File(root, "converted.png").path, output.path)
        assertArrayEquals(svg, source.readBytes())
        val bitmap = checkNotNull(BitmapFactory.decodeFile(output.path))
        try {
            assertEquals(400, bitmap.width)
            assertEquals(200, bitmap.height)
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            assertEquals(Color.RED, bitmap.getPixel(100, 100))
            assertEquals(Color.BLUE, bitmap.getPixel(300, 100))
        } finally { bitmap.recycle() }
    }

    @Test fun xmlExportProducesVectorPathsAndDoesNotAlterTheSource() = runBlocking<Unit> {
        val source = File(root, "source.svg").apply { writeBytes(svg) }
        val output = WorkspaceSvgExport.export(context, svg, WorkspaceSvgFormat.XML, "converted", WorkspaceLocation(root.path, "android"), 1, 1)
        val nodes = nodes(File(output.path).readText())
        assertEquals("vector", nodes.first().first)
        assertEquals(2, nodes.count { it.first == "path" })
        assertEquals(setOf("#FFFF0000", "#FF0000FF"), nodes.filter { it.first == "path" }.map { it.second.getValue("fillColor") }.toSet())
        assertArrayEquals(svg, source.readBytes())
    }

    @Test fun bothFormatsRejectExistingDestinationsWithoutChangingTheirContent() {
        for (format in WorkspaceSvgFormat.values()) {
            val destination = File(root, "existing.${format.extension}").apply { writeText("keep-existing") }
            assertThrows(IllegalStateException::class.java) { runBlocking {
                WorkspaceSvgExport.export(context, svg, format, "existing", WorkspaceLocation(root.path, "android"), 64, 32)
            } }
            assertEquals("keep-existing", destination.readText())
        }
        assertFalse(validSvgExportSize(0, 64))
        assertFalse(validSvgExportSize(4097, 64))
        assertFalse(validSvgExportSize(null, 64))
        assertTrue(validSvgExportSize(4096, 4096))
    }

    @Test fun everyBundledProviderSvgConvertsToXmlWithParsablePathGeometry() {
        val directories = checkNotNull(context.assets.list("model_logos"))
        var converted = 0
        for (directory in directories) {
            val files = context.assets.list("model_logos/$directory").orEmpty()
            for (file in files.filter { it.endsWith(".svg", true) }) {
                val bytes = context.assets.open("model_logos/$directory/$file").use { it.readBytes() }
                val xml = SvgVectorConverter.convert(bytes)
                val nodes = nodes(xml)
                assertEquals(directory, "vector", nodes.first().first)
                val paths = nodes.filter { it.first == "path" }
                assertTrue(directory, paths.isNotEmpty())
                paths.forEach { assertNotNull(directory, PathParser.createPathFromPathData(it.second.getValue("pathData"))) }
                converted++
            }
        }
        assertEquals(37, converted)
    }

    @Test fun offsetViewBoxAndNestedTransformsKeepTheirCoordinateOrder() {
        val xml = convert("""
            <svg xmlns="http://www.w3.org/2000/svg" width="40" height="20" viewBox="4 8 20 10">
              <g transform="translate(3,4) scale(2) rotate(30 5 6)">
                <path transform="matrix(1,0,0,1,7,8)" d="M4 8H24V18H4Z" fill="#123"/>
              </g>
            </svg>
        """)
        val groups = nodes(xml).filter { it.first == "group" }.map { it.second }
        assertEquals(-8f, groups[0].getValue("translateX").toFloat(), 0f)
        assertEquals(-16f, groups[0].getValue("translateY").toFloat(), 0f)
        assertEquals(2f, groups[0].getValue("scaleX").toFloat(), 0f)
        assertEquals("3", groups[1]["translateX"])
        assertEquals("2", groups[2]["scaleX"])
        assertEquals("30", groups[3]["rotation"])
        assertEquals("5", groups[3]["pivotX"])
        assertEquals("7", groups[4]["translateX"])
        assertEquals("#FF112233", nodes(xml).single { it.first == "path" }.second["fillColor"])
    }

    @Test fun inheritedCurrentColorStrokeAndRoundedShapesRetainTheirPaint() {
        val xml = convert("""
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" color="#f00">
              <g fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <circle cx="12" cy="12" r="4"/>
                <rect x="2" y="2" width="4" height="6" rx="1"/>
              </g>
            </svg>
        """)
        val paths = nodes(xml).filter { it.first == "path" }
        assertEquals(2, paths.size)
        for ((_, attributes) in paths) {
            assertEquals("#00000000", attributes["fillColor"])
            assertEquals("#FFFF0000", attributes["strokeColor"])
            assertEquals("2", attributes["strokeWidth"])
            assertEquals("round", attributes["strokeLineCap"])
            assertTrue(attributes.getValue("pathData").contains('A'))
        }
    }

    @Test fun inheritedGradientKeepsBoundsStopsAndTransparentColorAlpha() {
        val xml = convert("""
            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 24 24">
              <defs>
                <linearGradient id="base" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0" stop-color="#f00" stop-opacity=".5"/>
                  <stop offset="1" stop-color="#00f"/>
                </linearGradient>
                <linearGradient id="paint" xlink:href="#base"/>
              </defs>
              <rect x="2" y="4" width="10" height="16" fill="url(#paint)"/>
            </svg>
        """)
        val nodes = nodes(xml)
        val gradient = nodes.single { it.first == "gradient" }.second
        assertEquals("2", gradient["startX"])
        assertEquals("4", gradient["startY"])
        assertEquals(16.382023f, gradient.getValue("endX").toFloat(), 0.0001f)
        assertEquals(12.988764f, gradient.getValue("endY").toFloat(), 0.0001f)
        assertEquals(listOf("#80FF0000", "#FF0000FF"), nodes.filter { it.first == "item" }.map { it.second.getValue("color") })
        assertTrue(xml.contains("http://schemas.android.com/aapt"))
    }

    @Test fun unsupportedVectorEffectsFailExplicitlyAndPublishNoXml() {
        val effects = listOf(
            "<g opacity=\".5\"><path d=\"M0 0h10v10z\"/></g>",
            "<path d=\"M0 0h10v10z\" filter=\"url(#blur)\"/>",
            "<text x=\"0\" y=\"10\">Text</text>",
            "<path d=\"M0 0h10v10z\" transform=\"skewX(15)\"/>",
        )
        for (effect in effects) assertThrows(UnsupportedSvgFeature::class.java) {
            convert("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">$effect</svg>")
        }
        val unsupported = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path d=\"M0 0h10v10z\" mask=\"url(#mask)\"/></svg>".toByteArray()
        assertThrows(UnsupportedSvgFeature::class.java) { runBlocking {
            WorkspaceSvgExport.export(context, unsupported, WorkspaceSvgFormat.XML, "unsupported", WorkspaceLocation(root.path, "android"), 1, 1)
        } }
        assertFalse(File(root, "unsupported.xml").exists())
    }

    private fun convert(svg: String): String = SvgVectorConverter.convert(svg.trimIndent().toByteArray(Charsets.UTF_8))
    private fun nodes(xml: String): List<Pair<String, Map<String, String>>> {
        val parser = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true); setInput(xml.reader()) }
        val result = mutableListOf<Pair<String, Map<String, String>>>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) result += parser.name to
                (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
            parser.next()
        }
        return result
    }
}
