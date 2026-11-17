package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import android.content.ClipData
import android.content.Intent
import android.net.Uri

internal fun workspaceShareMimeType(types: List<String>): String {
    require(types.isNotEmpty())
    val distinct = types.toSet()
    if (distinct.size == 1) return distinct.single()
    val families = distinct.map { it.substringBefore('/') }.toSet()
    return if (families.size == 1) families.single() + "/*" else "*/*"
}

/** 系统分享一次携带全部 URI，并为每一项授予临时读取权限。 */
internal fun workspaceShareIntent(uris: ArrayList<Uri>, mimeTypes: List<String>): Intent {
    require(uris.isNotEmpty() && uris.size == mimeTypes.size)
    return Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = workspaceShareMimeType(mimeTypes)
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.single())
        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    }
}