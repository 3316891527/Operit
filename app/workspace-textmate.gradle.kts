import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.attributes.Usage

// 固定上游发布物，仅移除坐标转换与动态终点引用相关的四个类，由应用修正版提供。
val textMateLibrary = extensions.getByType<VersionCatalogsExtension>().named("libs").findLibrary("sora-textmate").get()
val workspaceTextMateEngine = configurations.create("workspaceTextMateEngine") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
}
dependencies.add(workspaceTextMateEngine.name, textMateLibrary)
val patchedTextMateAar = layout.buildDirectory.file("workspace-textmate/language-textmate-patched.aar")
val prepareWorkspaceTextMateEngine = tasks.register("prepareWorkspaceTextMateEngine") {
    inputs.files(workspaceTextMateEngine).withPropertyName("engineAar")
    outputs.file(patchedTextMateAar)
    doLast {
        val original = workspaceTextMateEngine.singleFile
        val expectedSha256 = "909576591348f999b318ef601568a208550ee1c0cae375d70626f70f2b7c63ba"
        val actualSha256 = MessageDigest.getInstance("SHA-256").digest(original.readBytes()).joinToString("") { "%02x".format(it) }
        check(actualSha256 == expectedSha256) { "TextMate AAR changed; review the workspace compatibility patches before upgrading" }
        val replacedClasses = setOf(
            "org/eclipse/tm4e/core/internal/rule/RegExpSource.class",
            "org/eclipse/tm4e/core/internal/oniguruma/OnigString.class",
            "org/eclipse/tm4e/core/internal/oniguruma/OnigString\$MultiByteString.class",
            "org/eclipse/tm4e/core/internal/oniguruma/OnigString\$SingleByteString.class",
        )
        val removed = mutableSetOf<String>()
        val output = patchedTextMateAar.get().asFile
        output.parentFile.mkdirs()
        val temporary = output.resolveSibling(output.name + ".tmp")
        ZipFile(original).use { input ->
            ZipOutputStream(temporary.outputStream()).use { archive ->
                for (entry in input.entries()) {
                    val data = input.getInputStream(entry).use { it.readBytes() }
                    archive.putNextEntry(ZipEntry(entry.name).apply { time = 0L })
                    if (entry.name == "classes.jar") {
                        val buffer = ByteArrayOutputStream()
                        ZipInputStream(ByteArrayInputStream(data)).use { classes ->
                            ZipOutputStream(buffer).use { patched ->
                                var item = classes.nextEntry
                                while (item != null) {
                                    val current = item
                                    if (current.name in replacedClasses) {
                                        removed += current.name
                                    } else {
                                        patched.putNextEntry(ZipEntry(current.name).apply { time = 0L })
                                        classes.copyTo(patched)
                                        patched.closeEntry()
                                    }
                                    item = classes.nextEntry
                                }
                            }
                        }
                        archive.write(buffer.toByteArray())
                    } else {
                        archive.write(data)
                    }
                    archive.closeEntry()
                }
            }
        }
        check(removed == replacedClasses) { "TextMate class inventory changed: $removed" }
        Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
// 文件依赖保留任务关联，所有编译与打包变体都会先取得唯一的修正版 AAR。
dependencies.add("implementation", files(patchedTextMateAar).builtBy(prepareWorkspaceTextMateEngine))
