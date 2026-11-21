package com.ai.assistance.operit.ui.features.conversation.workspace.git

internal data class GitReference(val name: String, val commit: String, val upstream: String?, val updatedAt: Long = 0)


/** 引用枚举不扫描 reflog 或悬空提交；远端缓存必须仍有对应的远端分支。 */
internal fun visibleReviewReferences(
    references: List<GitReference>, remoteHeads: Map<String, Set<String>>,
): List<GitReference> {
    fun present(name: String): Boolean {
        if (!name.startsWith("refs/remotes/")) return true
        val relative = name.removePrefix("refs/remotes/")
        val remote = remoteHeads.keys.filter { relative.startsWith("$it/") }.maxByOrNull(String::length)
        return remote != null && relative.removePrefix("$remote/") in checkNotNull(remoteHeads[remote])
    }
    return references.filter { reference ->
        present(reference.name)
    }.sortedWith(compareByDescending<GitReference> { it.updatedAt }.thenBy { it.name })
}

internal data class GitReferenceInventory(val references: List<GitReference>, val currentBranch: String?, val upstream: String?)

/** HEAD 符号引用仅用于定位，不作为可选择的独立分支。 */
internal fun parseReviewReferences(inventory: String): GitReferenceInventory {
    var current: String? = null
    var upstream: String? = null
    val references = inventory.lineSequence().filter { it.isNotEmpty() }.mapNotNull { line ->
        val parts = line.split('\u0000')
        require(parts.size == 6) { "Invalid Git ref inventory" }
        if (parts[5].isNotEmpty()) return@mapNotNull null
        val tracking = parts[3].takeIf { it.isNotEmpty() }
        if (parts[2] == "*") { current = parts[0]; upstream = tracking }
        GitReference(parts[0], parts[1], tracking, parts[4].toLong())
    }.toList()
    return GitReferenceInventory(references, current, upstream)
}

internal fun parseRemoteReviewHeads(inventory: String): Set<String> =
    inventory.lineSequence().filter { it.isNotEmpty() }.map { line ->
        val fields = line.split('\t')
        require(fields.size == 2 && fields[1].startsWith("refs/heads/")) { "Invalid remote branch inventory" }
        fields[1].removePrefix("refs/heads/")
    }.toSet()
