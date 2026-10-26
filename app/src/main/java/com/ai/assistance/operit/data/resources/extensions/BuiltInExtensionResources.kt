package com.ai.assistance.operit.data.resources.extensions

import android.content.Context
import com.ai.assistance.operit.data.resources.packs.PackResources
import com.ai.assistance.operit.data.resources.packs.ResourcePackSpec
import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class BuiltInExtensionSpec(
    val kind: String,
    val path: String,
    val packageNames: List<String>,
)

/** 内置身份只由 APK 固定清单与已校验安装记录授予，外部导入不能自行声明。 */
object BuiltInExtensionResources {
    fun specs(context: Context): List<ResourcePackSpec> =
        PackResources.catalog(context).packs.filter { it.extension != null }

    fun packageNames(context: Context): Set<String> =
        specs(context).flatMap { it.extension!!.packageNames }.toSet()

    fun installed(context: Context): List<Pair<ResourcePackSpec, File>> =
        specs(context).filter { !PackResources.isBundled(context, it.id) && PackResources.isReady(context, it.id) }
            .map { it to File(PackResources.directory(context, it.id), it.extension!!.path) }
}
