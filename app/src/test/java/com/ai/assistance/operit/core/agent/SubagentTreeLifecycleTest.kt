package com.ai.assistance.operit.core.agent

import org.junit.Assert.*
import org.junit.Test

internal fun treeTask(id: String, parent: SubagentTask? = null, root: String = parent?.rootChatId ?: "root", status: SubagentStatus = SubagentStatus.QUEUED): SubagentTask = SubagentTask(
    id = id, parentChatId = parent?.childChatId ?: root, childChatId = "$id-chat", parentToolCallId = "$id-call",
    title = id, goal = "完成有界任务", preset = SubagentPreset(id = "p", name = "帮手", description = "分析", systemPrompt = "独立工作"),
    model = SubagentModel(id = "m", configId = "c", modelName = "helper", capabilities = "分析"), executionId = "$id-execution",
    rootChatId = root, parentTaskId = parent?.id, agentName = id, path = (parent?.path ?: "") + "/$id", depth = (parent?.depth ?: 0) + 1,
    status = status, createdAt = 1, queuedAt = 1,
)

class SubagentTreeLifecycleTest {
    @Test fun topologyAllowsNestedNodesAndLimitsControlToAncestors() {
        val a = treeTask("a"); val b = treeTask("b", a); val c = treeTask("c"); val nodes = listOf(a, b, c)
        SubagentTree.validate(nodes)
        assertEquals(listOf(b.id), SubagentTree.descendants(nodes, a.childChatId).map { it.id })
        assertTrue(SubagentTree.canControl(nodes, "root", b))
        assertTrue(SubagentTree.canControl(nodes, a.childChatId, b))
        assertFalse(SubagentTree.canControl(nodes, c.childChatId, b))
        assertEquals("root", SubagentTree.rootFor(nodes, b.childChatId))
    }

    @Test fun invalidLinksPathsDepthAndDuplicateNamesAreRejected() {
        val a = treeTask("a"); val b = treeTask("b", a)
        for (invalid in listOf(b.copy(parentTaskId = "missing"), b.copy(parentChatId = "root"), b.copy(depth = 1), b.copy(path = "/wrong"), b.copy(rootChatId = "other"))) {
            assertThrows(IllegalArgumentException::class.java) { SubagentTree.validate(listOf(a, invalid)) }
        }
        assertThrows(IllegalArgumentException::class.java) { SubagentTree.validate(listOf(a, a.copy(id = "duplicate", childChatId = "another"))) }
        for (name in listOf("", "../a", "a/b", "a b", "x".repeat(49))) assertThrows(IllegalArgumentException::class.java) { SubagentTree.validateName(name) }
    }

    @Test fun crossBranchAndAncestorWaitCyclesAreRejected() {
        val a = treeTask("a", status = SubagentStatus.WAITING).copy(waitingFor = listOf("b"))
        val b = treeTask("b", status = SubagentStatus.WAITING).copy(waitingFor = listOf("c"))
        val c = treeTask("c")
        assertThrows(IllegalArgumentException::class.java) { SubagentTree.requireAcyclicWait(listOf(a, b, c), "c", listOf("a")) }
        SubagentTree.requireAcyclicWait(listOf(a, b, c), "a", listOf("c"))
    }

    @Test fun aWaitingParentReleasesTheOnlySlotForItsChild() {
        val parent = treeTask("parent", status = SubagentStatus.WAITING)
        val child = treeTask("child", parent)
        assertEquals(child, SubagentScheduling.next(listOf(parent, child), setOf(parent.id), emptySet(), SubagentLimits(1, 1), { false }))
        assertNull(SubagentScheduling.next(listOf(parent.copy(status = SubagentStatus.RUNNING), child), setOf(parent.id), emptySet(), SubagentLimits(1, 1), { false }))
    }

    @Test fun resumingReacquiresCapacityAndCannotExceedALoweredLimit() {
        val parent = treeTask("parent", status = SubagentStatus.WAITING)
        val peer = treeTask("peer", status = SubagentStatus.RUNNING)
        val queued = treeTask("queued")
        assertNull(SubagentScheduling.next(listOf(parent, peer, queued), setOf(parent.id, peer.id), setOf(parent.id), SubagentLimits(1, 1), { false }))
        assertEquals(parent, SubagentScheduling.next(listOf(parent, queued), setOf(parent.id), setOf(parent.id), SubagentLimits(1, 1), { false }))
    }

    @Test fun allLevelsShareTheRootBudgetButAnotherRootCanProceed() {
        val parent = treeTask("parent", status = SubagentStatus.RUNNING)
        val child = treeTask("child", parent)
        val other = treeTask("other", root = "another-root")
        assertEquals(other, SubagentScheduling.next(listOf(parent, child, other), setOf(parent.id), emptySet(), SubagentLimits(3, 1), { false }))
    }

    @Test fun stoppedBranchesAreNotScheduledAndQueueOrderIsStable() {
        val a = treeTask("a").copy(queuedAt = 3); val b = treeTask("b").copy(queuedAt = 2)
        assertEquals(1, SubagentTree.queuePosition(listOf(a, b), b.id))
        assertEquals(b, SubagentScheduling.next(listOf(a, b), emptySet(), emptySet(), SubagentLimits(), { false }))
        assertNull(SubagentScheduling.next(listOf(a, b), emptySet(), emptySet(), SubagentLimits(), { true }))
    }

    @Test fun terminalJobsRetainCapacityUntilCleanupFinishes() {
        val finishing = treeTask("old", status = SubagentStatus.COMPLETED); val next = treeTask("next")
        assertNull(SubagentScheduling.next(listOf(finishing, next), setOf(finishing.id), emptySet(), SubagentLimits(1, 1), { false }))
        assertEquals(next, SubagentScheduling.next(listOf(finishing, next), emptySet(), emptySet(), SubagentLimits(1, 1), { false }))
    }

    @Test fun overlappingCleanupCannotOpenTheDeletionGateEarly() {
        val gates = SubagentStopGates()
        gates.close("root"); gates.close("root"); gates.open("root")
        assertTrue(gates.contains("root"))
        gates.open("root"); assertFalse(gates.contains("root"))
        assertThrows(IllegalStateException::class.java) { gates.open("root") }
    }

    @Test fun waitingTransitionsAndRestartsPreserveExecutionOwnership() {
        val queued = treeTask("a"); val running = queued.copy(status = SubagentStatus.RUNNING)
        val waiting = running.copy(status = SubagentStatus.WAITING, waitingFor = listOf("b"))
        val done = running.copy(status = SubagentStatus.COMPLETED)
        SubagentTransitions.validate(queued, running)
        SubagentTransitions.validate(running, waiting)
        SubagentTransitions.validate(waiting, running)
        SubagentTransitions.validate(waiting, done)
        SubagentTransitions.validate(done, done.copy(executionId = "next", attempt = 2, status = SubagentStatus.QUEUED))
        assertThrows(IllegalStateException::class.java) { SubagentTransitions.validate(running, queued) }
        assertThrows(IllegalStateException::class.java) { SubagentTransitions.validate(done, running) }
        assertThrows(IllegalStateException::class.java) { SubagentTransitions.validate(done, done.copy(parentChatId = "other")) }
        assertThrows(IllegalStateException::class.java) { SubagentTransitions.validate(running, queued.copy(executionId = "stale", attempt = 2)) }
    }

    @Test fun rootsCannotBeReusedAsChildConversations() {
        val a = treeTask("a")
        val b = treeTask("b", root = a.childChatId)
        assertThrows(IllegalArgumentException::class.java) { SubagentTree.validate(listOf(a, b)) }
    }

    @Test fun persistedWaitCyclesAndDuplicateLettersAreRejected() {
        val a = treeTask("a", status = SubagentStatus.WAITING).copy(waitingFor = listOf("b"))
        val b = treeTask("b", status = SubagentStatus.WAITING).copy(waitingFor = listOf("a"))
        assertThrows(IllegalArgumentException::class.java) { SubagentTree.validate(listOf(a, b)) }
        val letter = SubagentLetter(invocationId = "call", senderTaskId = null, recipientTaskId = "a", content = "核对")
        assertThrows(IllegalArgumentException::class.java) { SubagentTree.validate(listOf(a.copy(waitingFor = emptyList(), letters = listOf(letter, letter)))) }
    }

    @Test fun queueNumbersStayWithinTheDisplayedRootTree() {
        val a = treeTask("a").copy(queuedAt = 2)
        val b = treeTask("b").copy(queuedAt = 3)
        val other = treeTask("other", root = "other-root")
        assertEquals(1, SubagentTree.queuePosition(listOf(a, b, other), a.id))
        assertEquals(2, SubagentTree.queuePosition(listOf(a, b, other), b.id))
        assertEquals(1, SubagentTree.queuePosition(listOf(a, b, other), other.id))
    }

    @Test fun immediateWaitObservationPrioritizesInputAndDetectsIdle() {
        assertEquals("mailbox", SubagentWaiting.outcome(hasInput = true, hasWork = true))
        assertEquals("mailbox", SubagentWaiting.outcome(hasInput = true, hasWork = false))
        assertEquals("idle", SubagentWaiting.outcome(hasInput = false, hasWork = false))
        assertNull(SubagentWaiting.outcome(hasInput = false, hasWork = true))
    }

    @Test fun changingTheStableAgentNameOrCreationTimeIsRejected() {
        val a = treeTask("a")
        assertThrows(IllegalStateException::class.java) { SubagentTransitions.validate(a, a.copy(agentName = "renamed")) }
        assertThrows(IllegalStateException::class.java) { SubagentTransitions.validate(a, a.copy(createdAt = 2)) }
    }
}