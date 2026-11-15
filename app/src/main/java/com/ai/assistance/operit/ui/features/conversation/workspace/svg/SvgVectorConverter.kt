package com.ai.assistance.operit.ui.features.conversation.workspace.svg

import android.graphics.Color
import android.graphics.RectF
import android.util.Xml
import androidx.core.graphics.PathParser
import com.ai.assistance.operit.ui.common.icons.ImageAssetLoader
import java.io.StringWriter
import java.util.Locale
import kotlin.math.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer

internal class UnsupportedSvgFeature(val feature: String) : IllegalArgumentException(feature)

/** 按 SVG 的坐标和绘制属性生成 VectorDrawable；不能准确表达的特性明确拒绝。 */
internal object SvgVectorConverter {
    private const val ANDROID = "http://schemas.android.com/apk/res/android"
    private const val AAPT = "http://schemas.android.com/aapt"
    private val number = Regex("[-+]?(?:\\d*\\.\\d+|\\d+\\.?\\d*)(?:[eE][-+]?\\d+)?")
    private val transform = Regex("([A-Za-z]+)\\s*\\(([^)]*)\\)")
    private val defaults = mapOf("fill" to "black", "stroke" to "none", "color" to "black",
        "fill-opacity" to "1", "stroke-opacity" to "1", "fill-rule" to "nonzero", "stroke-width" to "1",
        "stroke-linecap" to "butt", "stroke-linejoin" to "miter", "stroke-miterlimit" to "4", "visibility" to "visible")
    private val paintProperties = defaults.keys + setOf("opacity", "display", "stop-color", "stop-opacity",
        "clip-path", "mask", "filter", "stroke-dasharray", "stroke-dashoffset", "vector-effect", "paint-order",
        "marker-start", "marker-mid", "marker-end", "transform-origin")
    private val layoutProperties = setOf("flex", "line-height", "vertical-align", "width", "height", "overflow")
    private val ignoredTags = setOf("title", "desc", "metadata", "defs", "linearGradient", "radialGradient")
    private data class Node(val tag: String, val attributes: Map<String, String>, val children: MutableList<Node> = mutableListOf())
    private data class Stop(val offset: Float, val color: Int)
    private data class Paint(val color: Int? = null, val gradient: Node? = null, val stops: List<Stop> = emptyList())

    fun convert(bytes: ByteArray): String {
        val root = parse(bytes)
        require(root.tag == "svg") { "Expected an SVG root element" }
        val image = bytes.inputStream().use(ImageAssetLoader::readSvg)
        val viewBox = root.attributes["viewBox"]?.let(::numbers)
            ?: listOf(0f, 0f, image.width, image.height)
        require(viewBox.size == 4 && viewBox[2] > 0 && viewBox[3] > 0) { "Invalid SVG viewBox" }
        val output = StringWriter()
        val xml = Xml.newSerializer().apply {
            setOutput(output)
            startDocument("UTF-8", true)
            setPrefix("android", ANDROID)
            setPrefix("aapt", AAPT)
            startTag(null, "vector")
            attribute(ANDROID, "width", "${value(image.width)}dp")
            attribute(ANDROID, "height", "${value(image.height)}dp")
            attribute(ANDROID, "viewportWidth", value(image.width))
            attribute(ANDROID, "viewportHeight", value(image.height))
        }
        val writer = Writer(xml, root, viewBox)
        val preserve = root.attributes["preserveAspectRatio"] ?: "xMidYMid meet"
        val sx = image.width / viewBox[2]
        val sy = image.height / viewBox[3]
        val scale = if (preserve.endsWith("slice")) max(sx, sy) else min(sx, sy)
        val scaleX = if (preserve == "none") sx else scale
        val scaleY = if (preserve == "none") sy else scale
        if (preserve != "none" && !Regex("x(Min|Mid|Max)Y(Min|Mid|Max)( (meet|slice))?").matches(preserve)) {
            throw UnsupportedSvgFeature("preserveAspectRatio=$preserve")
        }
        val dx = (image.width - viewBox[2] * scaleX) * when {
            preserve.startsWith("xMin") || preserve == "none" -> 0f
            preserve.startsWith("xMax") -> 1f
            else -> 0.5f
        }
        val dy = (image.height - viewBox[3] * scaleY) * when {
            preserve.contains("YMin") || preserve == "none" -> 0f
            preserve.contains("YMax") -> 1f
            else -> 0.5f
        }
        writer.group(mapOf("translateX" to dx - viewBox[0] * scaleX, "translateY" to dy - viewBox[1] * scaleY,
            "scaleX" to scaleX, "scaleY" to scaleY))
        writer.emit(root, defaults, emptySet(), isRoot = true)
        xml.endTag(null, "group")
        require(writer.paths > 0) { "SVG contains no drawable paths" }
        xml.endTag(null, "vector")
        xml.endDocument()
        return output.toString()
    }

    private fun parse(bytes: ByteArray): Node {
        val parser = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true); setInput(bytes.inputStream(), null) }
        val stack = ArrayDeque<Node>()
        var root: Node? = null
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.DOCDECL -> throw UnsupportedSvgFeature("DOCTYPE")
                XmlPullParser.PROCESSING_INSTRUCTION -> if (parser.text.trimStart().startsWith("xml-stylesheet")) {
                    throw UnsupportedSvgFeature("xml-stylesheet")
                }
                XmlPullParser.ENTITY_REF -> throw UnsupportedSvgFeature("entity ${parser.name}")
                XmlPullParser.START_TAG -> {
                    val namespace = parser.namespace
                    val tag = if (namespace.isNullOrEmpty() || namespace == "http://www.w3.org/2000/svg") parser.name
                        else "{$namespace}${parser.name}"
                    val attributes = (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
                    val node = Node(tag, attributes)
                    if (stack.isEmpty()) { require(root == null); root = node } else stack.last().children.add(node)
                    stack.addLast(node)
                }
                XmlPullParser.END_TAG -> stack.removeLast()
            }
            parser.nextToken()
        }
        return requireNotNull(root) { "Empty SVG document" }
    }

    private class Writer(private val xml: XmlSerializer, root: Node, private val viewBox: List<Float>) {
        var paths = 0
        private val ids = mutableMapOf<String, Node>()
        init {
            fun collect(node: Node) {
                node.attributes["id"]?.let { require(ids.put(it, node) == null) { "Duplicate SVG id: $it" } }
                node.children.forEach(::collect)
            }
            collect(root)
        }

        fun group(attributes: Map<String, Float>) {
            xml.startTag(null, "group")
            attributes.forEach { (key, item) -> xml.attribute(ANDROID, key, value(item)) }
        }

        fun emit(node: Node, inherited: Map<String, String>, references: Set<String>, isRoot: Boolean = false) {
            if (node.tag in ignoredTags) return
            val style = style(node, inherited)
            if (style["display"] == "none") return
            for (property in listOf("clip-path", "mask", "filter", "stroke-dasharray", "vector-effect", "marker-start", "marker-mid", "marker-end", "transform-origin")) {
                style[property]?.takeIf { it != "none" && it.isNotBlank() }?.let { throw UnsupportedSvgFeature("$property=$it") }
            }
            style["paint-order"]?.takeIf { it != "normal" }?.let { throw UnsupportedSvgFeature("paint-order=$it") }
            style["stroke-dashoffset"]?.takeIf { length(it) != 0f }?.let { throw UnsupportedSvgFeature("stroke-dashoffset") }
            val transforms = node.attributes["transform"]?.let(::transforms).orEmpty()
            transforms.forEach(::group)
            when (node.tag) {
                "svg", "g" -> {
                    if (node.tag == "svg" && !isRoot) throw UnsupportedSvgFeature("nested svg")
                    if (opacity(style["opacity"] ?: "1") != 1f) throw UnsupportedSvgFeature("group opacity")
                    node.children.forEach { emit(it, style - setOf("opacity", "display"), references) }
                }
                "use" -> {
                    val reference = node.attributes["href"] ?: throw UnsupportedSvgFeature("use without href")
                    if (!reference.startsWith('#') || reference in references) throw UnsupportedSvgFeature("use=$reference")
                    val target = ids[reference.drop(1)] ?: throw UnsupportedSvgFeature("missing $reference")
                    group(mapOf("translateX" to coordinate(node, "x"), "translateY" to coordinate(node, "y")))
                    emit(target, style, references + reference)
                    xml.endTag(null, "group")
                }
                "path", "rect", "circle", "ellipse", "line", "polygon", "polyline" -> {
                    if (style["visibility"] != "hidden" && style["visibility"] != "collapse") {
                        geometry(node)?.let { path(it, style) }
                    }
                }
                else -> throw UnsupportedSvgFeature(node.tag)
            }
            repeat(transforms.size) { xml.endTag(null, "group") }
        }

        private fun path(data: String, style: Map<String, String>) {
            val parsed = requireNotNull(PathParser.createPathFromPathData(data)) { "Invalid SVG path" }
            val bounds = RectF().also { parsed.computeBounds(it, true) }
            val foreground = style.getValue("color")
            val fill = paint(style.getValue("fill"), foreground)
            val stroke = paint(style.getValue("stroke"), foreground)
            val alpha = opacity(style["opacity"] ?: "1")
            xml.startTag(null, "path")
            xml.attribute(ANDROID, "pathData", data)
            xml.attribute(ANDROID, "fillType", when (style.getValue("fill-rule")) {
                "nonzero" -> "nonZero"
                "evenodd" -> "evenOdd"
                else -> throw UnsupportedSvgFeature("fill-rule")
            })
            fill.color?.let { xml.attribute(ANDROID, "fillColor", color(it)) }
            stroke.color?.let { xml.attribute(ANDROID, "strokeColor", color(it)) }
            xml.attribute(ANDROID, "fillAlpha", value(alpha * opacity(style.getValue("fill-opacity"))))
            xml.attribute(ANDROID, "strokeAlpha", value(alpha * opacity(style.getValue("stroke-opacity"))))
            xml.attribute(ANDROID, "strokeWidth", value(length(style.getValue("stroke-width"))))
            for ((property, attribute, allowed) in listOf(
                Triple("stroke-linecap", "strokeLineCap", setOf("butt", "round", "square")),
                Triple("stroke-linejoin", "strokeLineJoin", setOf("miter", "round", "bevel")),
            )) {
                val setting = style.getValue(property)
                if (setting !in allowed) throw UnsupportedSvgFeature("$property=$setting")
                xml.attribute(ANDROID, attribute, setting)
            }
            xml.attribute(ANDROID, "strokeMiterLimit", value(length(style.getValue("stroke-miterlimit"))))
            fill.gradient?.let { gradient("fillColor", fill, bounds) }
            stroke.gradient?.let { gradient("strokeColor", stroke, bounds) }
            xml.endTag(null, "path")
            paths++
        }

        private fun paint(setting: String, foreground: String): Paint {
            if (setting == "none") return Paint(color = Color.TRANSPARENT)
            if (!setting.startsWith("url(")) return Paint(color = parseColor(if (setting == "currentColor") foreground else setting))
            val reference = Regex("url\\(\\s*['\"]?#([^)'\"\\s]+)['\"]?\\s*\\)").matchEntire(setting)?.groupValues?.get(1)
                ?: throw UnsupportedSvgFeature(setting)
            val node = gradientNode(reference, emptySet())
            val stops = node.children.filter { it.tag == "stop" }.map { stop ->
                val attributes = style(stop, emptyMap())
                val raw = attributes["stop-color"] ?: "black"
                val stopColor = parseColor(if (raw == "currentColor") foreground else raw)
                val alpha = (Color.alpha(stopColor) * opacity(attributes["stop-opacity"] ?: "1")).roundToInt()
                Stop(opacity(stop.attributes["offset"] ?: "0"), (stopColor and 0x00ffffff) or (alpha shl 24))
            }
            require(stops.isNotEmpty()) { "Gradient has no stops" }
            var previous = 0f
            val normalized = stops.map { it.copy(offset = max(previous, it.offset).also { next -> previous = next }) }
            if (normalized.size == 1) return Paint(color = normalized.single().color)
            return Paint(gradient = node, stops = normalized)
        }

        private fun gradientNode(id: String, references: Set<String>): Node {
            if (id in references) throw UnsupportedSvgFeature("cyclic gradient")
            val node = ids[id] ?: throw UnsupportedSvgFeature("missing gradient #$id")
            if (node.tag !in setOf("linearGradient", "radialGradient")) throw UnsupportedSvgFeature("paint server ${node.tag}")
            val href = node.attributes["href"] ?: return node
            if (!href.startsWith('#')) throw UnsupportedSvgFeature("gradient href=$href")
            val base = gradientNode(href.drop(1), references + id)
            return Node(node.tag, base.attributes + node.attributes,
                (if (node.children.any { it.tag == "stop" }) node.children else base.children).toMutableList())
        }

        private fun gradient(attribute: String, paint: Paint, bounds: RectF) {
            val node = checkNotNull(paint.gradient)
            node.attributes["gradientTransform"]?.takeIf { it.isNotBlank() }?.let { throw UnsupportedSvgFeature("gradientTransform") }
            val units = node.attributes["gradientUnits"] ?: "objectBoundingBox"
            if (units !in setOf("objectBoundingBox", "userSpaceOnUse")) throw UnsupportedSvgFeature("gradientUnits=$units")
            fun position(name: String, default: String, horizontal: Boolean): Float {
                val setting = node.attributes[name] ?: default
                val dimension = if (units == "objectBoundingBox") {
                    if (horizontal) bounds.width() else bounds.height()
                } else if (horizontal) viewBox[2] else viewBox[3]
                val origin = if (units == "objectBoundingBox") {
                    if (horizontal) bounds.left else bounds.top
                } else if (horizontal) viewBox[0] else viewBox[1]
                return when {
                    setting.endsWith('%') -> origin + setting.dropLast(1).toFloat() / 100f * dimension
                    units == "objectBoundingBox" -> origin + length(setting) * dimension
                    else -> length(setting)
                }
            }
            xml.startTag(AAPT, "attr").attribute(null, "name", "android:$attribute")
            xml.startTag(null, "gradient")
            xml.attribute(ANDROID, "tileMode", when (val spread = node.attributes["spreadMethod"] ?: "pad") {
                "pad" -> "clamp"
                "reflect" -> "mirror"
                "repeat" -> "repeat"
                else -> throw UnsupportedSvgFeature("spreadMethod=$spread")
            })
            if (node.tag == "linearGradient") {
                xml.attribute(ANDROID, "type", "linear")
                val startX = position("x1", "0%", true)
                val startY = position("y1", "0%", false)
                var endX = position("x2", "100%", true)
                var endY = position("y2", "0%", false)
                if (units == "objectBoundingBox") {
                    if (bounds.width() <= 0 || bounds.height() <= 0) throw UnsupportedSvgFeature("empty gradient bounding box")
                    val dx = (endX - startX) / bounds.width()
                    val dy = (endY - startY) / bounds.height()
                    val squared = dx * dx + dy * dy
                    if (squared == 0f) throw UnsupportedSvgFeature("zero-length gradient")
                    // 包围盒非等比变换后，渐变方向必须按逆转置换算，直接缩放端点会改变颜色分布。
                    val ax = dx / (squared * bounds.width())
                    val ay = dy / (squared * bounds.height())
                    val denominator = ax * ax + ay * ay
                    endX = startX + ax / denominator
                    endY = startY + ay / denominator
                }
                if (startX == endX && startY == endY) throw UnsupportedSvgFeature("zero-length gradient")
                xml.attribute(ANDROID, "startX", value(startX))
                xml.attribute(ANDROID, "startY", value(startY))
                xml.attribute(ANDROID, "endX", value(endX))
                xml.attribute(ANDROID, "endY", value(endY))
            } else {
                if (units == "objectBoundingBox" && abs(bounds.width() - bounds.height()) > 0.001f) throw UnsupportedSvgFeature("elliptical radialGradient")
                val cx = node.attributes["cx"] ?: "50%"
                val cy = node.attributes["cy"] ?: "50%"
                if ((node.attributes["fx"] ?: cx) != cx || (node.attributes["fy"] ?: cy) != cy) throw UnsupportedSvgFeature("radialGradient focal point")
                xml.attribute(ANDROID, "type", "radial")
                xml.attribute(ANDROID, "centerX", value(position("cx", "50%", true)))
                xml.attribute(ANDROID, "centerY", value(position("cy", "50%", false)))
                val radius = node.attributes["r"] ?: "50%"
                val basis = if (units == "objectBoundingBox") bounds.width() else hypot(viewBox[2], viewBox[3]) / sqrt(2f)
                val r = if (radius.endsWith('%')) radius.dropLast(1).toFloat() / 100f * basis
                    else if (units == "objectBoundingBox") length(radius) * basis else length(radius)
                require(r > 0)
                xml.attribute(ANDROID, "gradientRadius", value(r))
            }
            paint.stops.forEach {
                xml.startTag(null, "item").attribute(ANDROID, "offset", value(it.offset)).attribute(ANDROID, "color", color(it.color)).endTag(null, "item")
            }
            xml.endTag(null, "gradient").endTag(AAPT, "attr")
        }
    }

    private fun style(node: Node, inherited: Map<String, String>): Map<String, String> {
        val result = inherited.toMutableMap()
        val declarations = node.attributes["style"].orEmpty().split(';').filter(String::isNotBlank).associate {
            require(':' in it) { "Invalid SVG style" }
            it.substringBefore(':').trim() to it.substringAfter(':').trim()
        }
        for ((key, setting) in node.attributes.filterKeys { it in paintProperties } + declarations) {
            when {
                key in layoutProperties -> Unit
                key !in paintProperties -> throw UnsupportedSvgFeature("style $key")
                setting == "inherit" || key == "color" && setting == "currentColor" -> Unit
                setting == "initial" -> { defaults[key]?.let { result[key] = it } }
                else -> result[key] = setting
            }
        }
        return result
    }

    private fun geometry(node: Node): String? {
        fun n(name: String, default: Float = 0f) = node.attributes[name]?.let(::length) ?: default
        fun f(item: Float) = value(item)
        return when (node.tag) {
            "path" -> node.attributes["d"]?.takeIf(String::isNotBlank)
            "line" -> "M${f(n("x1"))},${f(n("y1"))} L${f(n("x2"))},${f(n("y2"))}"
            "polygon", "polyline" -> {
                val points = numbers(node.attributes["points"].orEmpty())
                require(points.size >= 4 && points.size % 2 == 0) { "Invalid SVG points" }
                points.chunked(2).mapIndexed { index, pair -> "${if (index == 0) 'M' else 'L'}${f(pair[0])},${f(pair[1])}" }.joinToString(" ") +
                    if (node.tag == "polygon") " Z" else ""
            }
            "circle", "ellipse" -> {
                val cx = n("cx"); val cy = n("cy")
                val rx = if (node.tag == "circle") n("r") else n("rx")
                val ry = if (node.tag == "circle") rx else n("ry")
                require(rx >= 0 && ry >= 0)
                if (rx == 0f || ry == 0f) null else "M${f(cx - rx)},${f(cy)} A${f(rx)},${f(ry)} 0 1,0 ${f(cx + rx)},${f(cy)} A${f(rx)},${f(ry)} 0 1,0 ${f(cx - rx)},${f(cy)} Z"
            }
            "rect" -> {
                val x = n("x"); val y = n("y"); val width = n("width"); val height = n("height")
                val rx = min(n("rx", n("ry")), width / 2f); val ry = min(n("ry", n("rx")), height / 2f)
                require(width >= 0 && height >= 0 && rx >= 0 && ry >= 0)
                when {
                    width == 0f || height == 0f -> null
                    rx == 0f || ry == 0f -> "M${f(x)},${f(y)} h${f(width)} v${f(height)} h${f(-width)} Z"
                    else -> "M${f(x + rx)},${f(y)} H${f(x + width - rx)} A${f(rx)},${f(ry)} 0 0,1 ${f(x + width)},${f(y + ry)} V${f(y + height - ry)} A${f(rx)},${f(ry)} 0 0,1 ${f(x + width - rx)},${f(y + height)} H${f(x + rx)} A${f(rx)},${f(ry)} 0 0,1 ${f(x)},${f(y + height - ry)} V${f(y + ry)} A${f(rx)},${f(ry)} 0 0,1 ${f(x + rx)},${f(y)} Z"
                }
            }
            else -> throw UnsupportedSvgFeature(node.tag)
        }
    }

    private fun transforms(input: String): List<Map<String, Float>> {
        require(transform.replace(input, "").all { it.isWhitespace() || it == ',' }) { "Invalid SVG transform" }
        return transform.findAll(input).map { match ->
            val values = numbers(match.groupValues[2])
            fun count(vararg sizes: Int) { require(values.size in sizes) { "Invalid ${match.groupValues[1]} transform" } }
            when (match.groupValues[1]) {
                "translate" -> { count(1, 2); mapOf("translateX" to values[0], "translateY" to if (values.size == 2) values[1] else 0f) }
                "scale" -> { count(1, 2); mapOf("scaleX" to values[0], "scaleY" to if (values.size == 2) values[1] else values[0]) }
                "rotate" -> { count(1, 3); if (values.size == 1) mapOf("rotation" to values[0]) else mapOf("rotation" to values[0], "pivotX" to values[1], "pivotY" to values[2]) }
                "matrix" -> {
                    count(6)
                    val (a, b, c, d, e) = values
                    val f = values[5]
                    val scaleX = hypot(a, b)
                    if (scaleX == 0f || abs(a * c + b * d) > 0.0001f * max(1f, scaleX * hypot(c, d))) throw UnsupportedSvgFeature("skew matrix")
                    mapOf("translateX" to e, "translateY" to f, "scaleX" to scaleX, "scaleY" to (a * d - b * c) / scaleX,
                        "rotation" to (atan2(b, a) * 180f / PI.toFloat()))
                }
                else -> throw UnsupportedSvgFeature("transform ${match.groupValues[1]}")
            }
        }.toList()
    }

    private fun coordinate(node: Node, name: String): Float = node.attributes[name]?.let(::length) ?: 0f
    private fun length(input: String): Float {
        val match = Regex("(${number.pattern})(px|pt|pc|mm|cm|in)?").matchEntire(input.trim()) ?: throw UnsupportedSvgFeature("length=$input")
        val scale = when (match.groupValues[2]) { "pt" -> 96f / 72f; "pc" -> 16f; "mm" -> 96f / 25.4f; "cm" -> 96f / 2.54f; "in" -> 96f; else -> 1f }
        return (match.groupValues[1].toFloat() * scale).also { require(it.isFinite()) }
    }
    private fun opacity(input: String): Float = (if (input.endsWith('%')) input.dropLast(1).toFloat() / 100f else input.toFloat()).also { require(it.isFinite()) }.coerceIn(0f, 1f)
    private fun numbers(input: String): List<Float> {
        require(number.replace(input, "").all { it.isWhitespace() || it == ',' }) { "Invalid SVG numbers" }
        return number.findAll(input).map { it.value.toFloat().also { item -> require(item.isFinite()) } }.toList()
    }
    private fun parseColor(input: String): Int {
        if (input == "transparent") return Color.TRANSPARENT
        if (input.startsWith("rgb(") || input.startsWith("rgba(")) {
            val channels = input.substringAfter('(').substringBeforeLast(')').trim().split(Regex("[,\\s/]+")).filter(String::isNotBlank)
            require(channels.size == 3 || channels.size == 4)
            fun channel(index: Int) = (if (channels[index].endsWith('%')) channels[index].dropLast(1).toFloat() * 2.55f else channels[index].toFloat()).roundToInt().coerceIn(0, 255)
            return Color.argb(if (channels.size == 4) (opacity(channels[3]) * 255).roundToInt() else 255, channel(0), channel(1), channel(2))
        }
        val setting = when {
            input.startsWith('#') && input.length == 4 -> "#" + input.drop(1).map { "$it$it" }.joinToString("")
            input.startsWith('#') && input.length == 5 -> "#" + input[4].toString().repeat(2) + input.substring(1, 4).map { "$it$it" }.joinToString("")
            input.startsWith('#') && input.length == 9 -> "#" + input.takeLast(2) + input.substring(1, 7)
            else -> input
        }
        return try { Color.parseColor(setting) } catch (_: IllegalArgumentException) { throw UnsupportedSvgFeature("color=$input") }
    }
    private fun color(value: Int): String = String.format(Locale.ROOT, "#%08X", value)
    private fun value(number: Float): String = number.also { require(it.isFinite()) }.toBigDecimal().stripTrailingZeros().toPlainString()
}
