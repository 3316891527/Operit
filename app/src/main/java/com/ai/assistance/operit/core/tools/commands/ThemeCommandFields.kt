package com.ai.assistance.operit.core.tools.commands

import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** 命令只开放主题偏好白名单；读取值不代表主题脚本覆盖后的渲染结果。 */
internal object ThemeCommandFields {
    val sections: Set<String> = linkedSetOf("colors", "fonts", "avatars", "background", "messages", "info")
    val profileNames: Set<String> = setOf("global_user_avatar_uri", "global_user_name")
    val resourceKinds: Map<String, String> = linkedMapOf(
        "background_image_uri" to "image",
        "custom_user_avatar_uri" to "image",
        "custom_ai_avatar_uri" to "image",
        "global_user_avatar_uri" to "image",
        "bubble_user_image_uri" to "image",
        "bubble_ai_image_uri" to "image",
        "custom_font_path" to "font",
        "bubble_user_custom_font_path" to "font",
        "bubble_ai_custom_font_path" to "font",
    )

    private data class Field(
        val name: String,
        val section: String,
        val type: String,
        val nullable: Boolean = false,
        val range: ClosedFloatingPointRange<Float>? = null,
        val choices: List<String>? = null,
        val unit: String? = null,
    )

    private val fields: List<Field> = buildList {
        fun bool(section: String, vararg names: String) {
            names.forEach { add(Field(it, section, "boolean")) }
        }
        fun choice(section: String, name: String, vararg choices: String) {
            add(Field(name, section, "string", choices = choices.toList()))
        }
        fun number(section: String, name: String, range: ClosedFloatingPointRange<Float>, unit: String, nullable: Boolean = false) {
            add(Field(name, section, "number", nullable, range, unit = unit))
        }
        fun resource(section: String, name: String) {
            add(Field(name, section, "string", nullable = true))
        }
        bool("colors", "use_system_theme", "use_custom_colors")
        choice("colors", "theme_mode", "light", "dark")
        choice("colors", "on_color_mode", "auto", "light", "dark")
        listOf("custom_primary_color", "custom_secondary_color").forEach {
            add(Field(it, "colors", "color", nullable = true))
        }
        add(Field("thinking_particle_color", "colors", "color"))
        for (prefix in listOf("", "bubble_user_", "bubble_ai_")) {
            bool("fonts", prefix + "use_custom_font")
            choice("fonts", prefix + "font_type", "system", "file")
            choice("fonts", prefix + "system_font_name", "default", "serif", "sans-serif", "monospace", "cursive")
            resource("fonts", prefix + "custom_font_path")
        }
        number("fonts", "font_scale", 0.8f..1.5f, "scale")
        listOf("custom_user_avatar_uri", "custom_ai_avatar_uri", "global_user_avatar_uri").forEach {
            resource("avatars", it)
        }
        add(Field("global_user_name", "avatars", "string"))
        choice("avatars", "avatar_shape", "circle", "square")
        number("avatars", "avatar_corner_radius", 0f..16f, "dp")
        bool("background", "use_background_image", "use_background_blur", "video_background_muted", "video_background_loop")
        resource("background", "background_image_uri")
        choice("background", "background_media_type", "image", "video")
        number("background", "background_image_opacity", 0f..1f, "opacity")
        number("background", "background_blur_radius", 0f..25f, "dp")
        choice("messages", "user_chat_style", "cursor", "bubble")
        choice("messages", "ai_chat_style", "cursor", "bubble")
        bool("messages", "bubble_show_avatar", "bubble_wide_layout_enabled", "cursor_user_bubble_follow_theme")
        for (prefix in listOf("cursor_user_bubble", "bubble_user_bubble", "bubble_ai_bubble")) {
            bool("messages", prefix + "_liquid_glass", prefix + "_water_glass")
        }
        listOf("cursor_user_bubble_color", "bubble_user_bubble_color", "bubble_ai_bubble_color", "bubble_user_text_color", "bubble_ai_text_color").forEach {
            add(Field(it, "messages", "color", nullable = true))
        }
        listOf("cursor_user_bubble_opacity", "cursor_ai_bubble_opacity", "bubble_user_bubble_opacity", "bubble_ai_bubble_opacity").forEach {
            number("messages", it, 0f..1f, "opacity", nullable = true)
        }
        bool("messages", "bubble_rounded_corners_enabled", "bubble_ai_rounded_corners_enabled")
        choice("messages", "bubble_image_render_mode", "tiled_nine_slice", "nine_patch")
        for (side in listOf("user", "ai")) {
            val prefix = "bubble_${side}_"
            bool("messages", prefix + "use_image")
            resource("messages", prefix + "image_uri")
            for (edge in listOf("left", "top", "right", "bottom")) {
                number("messages", prefix + "image_crop_" + edge, 0f..0.45f, "ratio")
            }
            // 九宫格资源的自动解析可以产生端点 0 和 1，命令保留该存储语义。
            for (axis in listOf("", "y_")) {
                number("messages", prefix + "image_repeat_" + axis + "start", 0f..1f, "ratio")
                number("messages", prefix + "image_repeat_" + axis + "end", 0f..1f, "ratio")
            }
            number("messages", prefix + "image_scale", 0.2f..3f, "scale")
            val paddingPrefix = if (side == "user") "bubble_" else "bubble_ai_"
            number("messages", paddingPrefix + "content_padding_left", 0f..32f, "dp")
            number("messages", paddingPrefix + "content_padding_right", 0f..32f, "dp")
        }
        bool("info", "show_thinking_process", "show_status_tags", "show_model_provider", "show_model_name",
            "show_role_name", "show_user_name", "show_message_token_stats", "show_message_timing_stats",
            "show_message_token_speed", "show_message_timestamp", "show_input_processing_status", "show_chat_floating_dots_animation")
    }
    private val byName = fields.associateBy { it.name }

    fun describe(section: String?): JSONArray = JSONArray().apply {
        selected(section).forEach { field ->
            put(JSONObject().apply {
                put("name", field.name)
                put("section", field.section)
                put("type", field.type)
                put("nullable", field.nullable)
                put("scope", if (field.name in profileNames) "user" else "theme")
                field.range?.let { put("range", JSONObject().put("min", it.start).put("max", it.endInclusive)) }
                field.choices?.let { put("choices", JSONArray(it)) }
                field.unit?.let { put("unit", it) }
                if (field.type == "color") put("format", "#AARRGGBB or #RRGGBB")
                resourceKinds[field.name]?.let { put("resource_kind", it) }
                when (field.name) {
                    "theme_mode" -> put("description", "Stored manual light/dark choice; set use_system_theme=false to make it active.")
                    "use_system_theme" -> put("description", "True follows the system light/dark mode; false uses the stored theme_mode.")
                    "use_custom_font" -> put("description", "False restores the system default font without removing the saved font selection or file path.")
                    "bubble_user_use_custom_font", "bubble_ai_use_custom_font" -> put("description", "False follows the global theme font without removing the saved message font selection or file path.")
                    "background_image_uri" -> put("description", "Stored media reference; enable separately with use_background_image=true. background_media_type selects image or video. Null removes the reference and disables the background.")
                    "global_user_name" -> put("description", "Global user name; an empty string clears the displayed custom name.")
                    "custom_primary_color", "custom_secondary_color" -> put("description", "A nonnull color enables use_custom_colors; an explicit false switch conflicts with this operation.")
                    "cursor_user_bubble_color" -> put("description", "A nonnull color disables cursor_user_bubble_follow_theme and copies alpha to cursor_user_bubble_opacity unless opacity is explicit.")
                    "bubble_user_bubble_color", "bubble_ai_bubble_color" -> put("description", "A nonnull color copies alpha to the matching bubble opacity unless opacity is explicit.")
                }
                if (field.name.endsWith("custom_font_path")) {
                    put("description", "A nonnull path selects file font and enables custom font; null removes the path and disables an active file font.")
                }
                if (field.name == "bubble_user_image_uri" || field.name == "bubble_ai_image_uri") {
                    put("description", "A nonnull image enables this bubble image when glass is off; null removes the image and disables it.")
                }
                if (field.name.endsWith("image_repeat_start") || field.name.endsWith("image_repeat_y_start")) {
                    put("description", "Must be less than the matching image_repeat end value after the patch.")
                }
            })
        }
    }

    /** 只返回存储偏好及模型默认值；用户资料由服务从其独立存储读取。 */
    fun read(values: ThemePreferenceValues, section: String?): JSONObject = JSONObject().apply {
        val defaults = ThemePreferenceValues.defaultVisual()
        selected(section).filter { it.name !in profileNames }.forEach { field ->
            val value: Any? = when (field.type) {
                "boolean" -> values.boolean(field.name) ?: defaults.boolean(field.name)
                "number" -> values.float(field.name) ?: defaults.float(field.name)
                "color" -> (values.int(field.name) ?: defaults.int(field.name))?.let(::colorText)
                else -> values.string(field.name) ?: defaults.string(field.name)
            }
            put(field.name, value ?: JSONObject.NULL)
        }
    }

    fun validate(changes: JSONObject): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        val keys = changes.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val field = requireNotNull(byName[name]) { "Unknown theme field: $name" }
            result[name] = normalize(field, changes.get(name), normalized = false)
        }
        checkExplicitDependencies(result)
        return result
    }

    /** 补丁保留所有未涉及的字段，包括主题脚本引用；关联字段只按对应 UI 操作更新。 */
    fun apply(current: ThemePreferenceValues, changes: Map<String, Any?>): ThemePreferenceValues {
        require(changes.keys.none { it in profileNames }) { "User profile fields require target_type=user" }
        changes.forEach { (name, value) ->
            val field = requireNotNull(byName[name]) { "Unknown theme field: $name" }
            normalize(field, value, normalized = true)
        }
        checkExplicitDependencies(changes)
        val patch = changes.toMutableMap()
        fun implied(name: String, value: Any?) {
            require(name !in changes || changes[name] == value) { "Conflicting fields: $name" }
            patch[name] = value
        }
        fun string(name: String): String? = if (name in patch) patch[name] as String? else current.string(name)
        fun boolean(name: String): Boolean = if (name in patch) patch[name] == true else current.boolean(name) == true
        if (listOf("custom_primary_color", "custom_secondary_color").any { it in changes && changes[it] != null }) {
            implied("use_custom_colors", true)
        }
        if (changes["cursor_user_bubble_color"] != null) implied("cursor_user_bubble_follow_theme", false)
        for ((color, opacity) in mapOf(
            "cursor_user_bubble_color" to "cursor_user_bubble_opacity",
            "bubble_user_bubble_color" to "bubble_user_bubble_opacity",
            "bubble_ai_bubble_color" to "bubble_ai_bubble_opacity",
        )) {
            val selectedColor = changes[color] as Int?
            // 独立透明度字段可覆盖颜色中的透明通道；未指定时与颜色选择器保持一致。
            if (selectedColor != null && opacity !in changes) {
                patch[opacity] = ((selectedColor ushr 24) and 0xFF) / 255f
            }
        }
        for (prefix in listOf("", "bubble_user_", "bubble_ai_")) {
            val path = prefix + "custom_font_path"
            val type = prefix + "font_type"
            val enabled = prefix + "use_custom_font"
            val system = prefix + "system_font_name"
            if (changes[path] != null) {
                implied(type, "file")
                implied(enabled, true)
            }
            if (system in changes) {
                implied(type, "system")
                implied(enabled, true)
            }
            if (type in changes) implied(enabled, true)
            if (path in changes && changes[path] == null && string(type) == "file") implied(enabled, false)
            if (changes.keys.any { it in setOf(path, type, enabled, system) } && boolean(enabled) && string(type) == "file") {
                require(!string(path).isNullOrBlank()) { "$path is required for an enabled file font" }
            }
        }
        for (prefix in listOf("cursor_user_bubble", "bubble_user_bubble", "bubble_ai_bubble")) {
            val liquid = prefix + "_liquid_glass"
            val water = prefix + "_water_glass"
            if (changes[liquid] == true) implied(water, false)
            if (changes[water] == true) implied(liquid, false)
            if (prefix != "cursor_user_bubble" && (changes[liquid] == true || changes[water] == true)) {
                implied(prefix.removeSuffix("bubble") + "use_image", false)
            }
        }
        for (side in listOf("user", "ai")) {
            val prefix = "bubble_${side}_"
            val uri = prefix + "image_uri"
            val enabled = prefix + "use_image"
            val glass = boolean(prefix + "bubble_liquid_glass") || boolean(prefix + "bubble_water_glass")
            if (uri in changes) {
                if (changes[uri] == null) implied(enabled, false)
                else if (enabled !in changes) patch[enabled] = !glass
            }
            if (changes.keys.any { it == uri || it == enabled || it == prefix + "bubble_liquid_glass" || it == prefix + "bubble_water_glass" } && boolean(enabled)) {
                require(!glass) { "$enabled conflicts with message glass" }
                require(!string(uri).isNullOrBlank()) { "$uri is required when $enabled is true" }
            }
            for (axis in listOf("", "y_")) {
                val start = prefix + "image_repeat_" + axis + "start"
                val end = prefix + "image_repeat_" + axis + "end"
                if (start in changes || end in changes) {
                    val defaults = ThemePreferenceValues.defaultVisual()
                    val left = (patch[start] as Float?) ?: current.float(start) ?: defaults.requiredFloat(start)
                    val right = (patch[end] as Float?) ?: current.float(end) ?: defaults.requiredFloat(end)
                    require(left < right) { "$start must be less than $end" }
                }
            }
        }
        if ("background_image_uri" in changes && changes["background_image_uri"] == null) {
            implied("use_background_image", false)
        }
        if (changes["use_background_image"] == true) {
            require(!string("background_image_uri").isNullOrBlank()) { "background_image_uri is required when use_background_image is true" }
        }
        var result = current
        patch.forEach { (name, value) ->
            result = when (byName.getValue(name).type) {
                "boolean" -> result.withBoolean(name, value as Boolean)
                "color" -> result.withInt(name, value as Int?)
                "number" -> if (value == null) result.copy(floats = result.floats - name) else result.withFloat(name, value as Float)
                else -> result.withString(name, value as String?)
            }
        }
        return result
    }

    private fun selected(section: String?): List<Field> {
        require(section == null || section in sections) { "Unknown theme section: $section" }
        return fields.filter { section == null || it.section == section }
    }

    private fun normalize(field: Field, raw: Any?, normalized: Boolean): Any? {
        require(!normalized || raw !== JSONObject.NULL) { "${field.name} must use Kotlin null in a normalized patch" }
        val value = if (raw === JSONObject.NULL) null else raw
        if (value == null) {
            require(field.nullable) { "${field.name} cannot be null" }
            return null
        }
        return when (field.type) {
            "boolean" -> {
                require(value is Boolean) { "${field.name} must be a JSON boolean" }
                value
            }
            "number" -> {
                require(if (normalized) value is Float else value is Number) { "${field.name} must be a JSON number" }
                val number = value as Number
                val range = requireNotNull(field.range)
                // JSON 十进制先精确比较，禁止越界输入经 Double 或 Float 舍入后进入补丁。
                require(number.toDouble().isFinite()) { "${field.name} must be finite" }
                val inRange = if (normalized) {
                    number.toFloat() in range
                } else {
                    val decimal = requireNotNull(number.toString().toBigDecimalOrNull()) { "${field.name} must be a JSON number" }
                    decimal >= range.start.toString().toBigDecimal() && decimal <= range.endInclusive.toString().toBigDecimal()
                }
                require(inRange) { "${field.name} must be within ${range.start}..${range.endInclusive}" }
                number.toFloat().also { require(it.isFinite() && it in range) }
            }
            "color" -> {
                if (normalized) {
                    require(value is Int) { "${field.name} must be a normalized color Int" }
                    value
                } else {
                    require(value is String && Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?").matches(value)) {
                        "${field.name} must be #RRGGBB or #AARRGGBB"
                    }
                    val hex = value.substring(1)
                    (if (hex.length == 6) 0xFF000000L or hex.toLong(16) else hex.toLong(16)).toInt()
                }
            }
            else -> {
                require(value is String) { "${field.name} must be a JSON string" }
                field.choices?.let { require(value in it) { "${field.name} must be one of ${it.joinToString()}" } }
                if (field.name in resourceKinds) require(value.isNotBlank()) { "${field.name} must be nonblank or null" }
                value
            }
        }
    }

    private fun checkExplicitDependencies(changes: Map<String, Any?>) {
        fun conflict(condition: Boolean, message: String) { require(!condition) { message } }
        conflict(changes["use_custom_colors"] == false && listOf("custom_primary_color", "custom_secondary_color").any { changes[it] != null }, "Custom colors conflict with use_custom_colors=false")
        conflict(changes["cursor_user_bubble_follow_theme"] == true && changes["cursor_user_bubble_color"] != null, "Cursor color conflicts with following theme")
        for (prefix in listOf("", "bubble_user_", "bubble_ai_")) {
            val type = prefix + "font_type"
            val enabled = prefix + "use_custom_font"
            val path = prefix + "custom_font_path"
            val system = prefix + "system_font_name"
            conflict(changes[path] != null && (changes[type] == "system" || system in changes || changes[enabled] == false), "Conflicting font fields: $path")
            conflict(system in changes && (changes[type] == "file" || changes[enabled] == false), "Conflicting font fields: $system")
            conflict(type in changes && changes[enabled] == false, "Conflicting font fields: $type")
            conflict(path in changes && changes[path] == null && changes[type] == "file", "A file font requires $path")
        }
        for (prefix in listOf("cursor_user_bubble", "bubble_user_bubble", "bubble_ai_bubble")) {
            val liquid = prefix + "_liquid_glass"
            val water = prefix + "_water_glass"
            conflict(changes[liquid] == true && changes[water] == true, "Message glass modes are mutually exclusive: $prefix")
            if (prefix != "cursor_user_bubble") {
                val image = prefix.removeSuffix("bubble") + "use_image"
                conflict((changes[liquid] == true || changes[water] == true) && changes[image] == true, "Message glass conflicts with $image")
            }
        }
        for (side in listOf("user", "ai")) {
            val prefix = "bubble_${side}_"
            conflict(prefix + "image_uri" in changes && changes[prefix + "image_uri"] == null && changes[prefix + "use_image"] == true, "An image bubble requires ${prefix}image_uri")
            for (axis in listOf("", "y_")) {
                val start = prefix + "image_repeat_" + axis + "start"
                val end = prefix + "image_repeat_" + axis + "end"
                if (start in changes && end in changes) {
                    require((changes.getValue(start) as Float) < (changes.getValue(end) as Float)) { "$start must be less than $end" }
                }
            }
        }
        conflict("background_image_uri" in changes && changes["background_image_uri"] == null && changes["use_background_image"] == true, "An enabled background requires background_image_uri")
    }

    private fun colorText(value: Int): String = String.format(Locale.ROOT, "#%08X", value)
}
