package com.ai.assistance.operit.core.chat.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceHoldGestureTest {
    private fun prepared(): Pair<VoiceHoldGesture, Long> {
        val gesture = VoiceHoldGesture()
        val id = gesture.begin(180f, 700f, 48f)!!
        gesture.setTarget(VoiceHoldAction.CANCEL, VoiceHoldBounds(20f, 500f, 140f, 600f))
        gesture.setTarget(VoiceHoldAction.TRANSCRIBE, VoiceHoldBounds(220f, 500f, 340f, 600f))
        return gesture to id
    }

    @Test fun aShortPressWithoutStartingCannotSend() {
        val gesture = VoiceHoldGesture()
        gesture.release(0)
        assertNull(gesture.takeReleasedAction())
        assertEquals(VoiceHoldPhase.IDLE, gesture.state.phase)
    }

    @Test fun holdingAndMovingNeverDispatchBeforeRelease() {
        val (gesture, id) = prepared()
        gesture.move(id, 280f, 550f)
        assertEquals(VoiceHoldAction.TRANSCRIBE, gesture.state.action)
        assertNull(gesture.takeReleasedAction())
        assertEquals(VoiceHoldPhase.HOLDING, gesture.state.phase)
    }

    @Test fun releasingInTheCenterSendsExactlyOnce() {
        val (gesture, id) = prepared()
        gesture.release(id)
        assertEquals(VoiceHoldAction.SEND, gesture.takeReleasedAction())
        gesture.release(id)
        assertNull(gesture.takeReleasedAction())
    }

    @Test fun releasingInsideTheLeftTargetCancels() {
        val (gesture, id) = prepared()
        gesture.move(id, 80f, 550f)
        gesture.release(id)
        assertEquals(VoiceHoldAction.CANCEL, gesture.takeReleasedAction())
    }

    @Test fun releasingInsideTheRightTargetTranscribes() {
        val (gesture, id) = prepared()
        gesture.move(id, 280f, 550f)
        gesture.release(id)
        assertEquals(VoiceHoldAction.TRANSCRIBE, gesture.takeReleasedAction())
    }

    @Test fun movingBackOutOfATargetReturnsToSendingAudio() {
        val (gesture, id) = prepared()
        gesture.move(id, 80f, 550f)
        gesture.move(id, 180f, 650f)
        gesture.release(id)
        assertEquals(VoiceHoldAction.SEND, gesture.takeReleasedAction())
    }

    @Test fun minorMovementCannotActivateATargetOverTheOriginalButton() {
        val (gesture, id) = prepared()
        gesture.setTarget(VoiceHoldAction.TRANSCRIBE, VoiceHoldBounds(150f, 650f, 210f, 740f))
        gesture.move(id, 190f, 705f)
        gesture.release(id)
        assertEquals(VoiceHoldAction.SEND, gesture.takeReleasedAction())
    }

    @Test fun systemCancellationCannotTurnIntoARelease() {
        val (gesture, id) = prepared()
        gesture.cancel(id)
        gesture.release(id)
        assertNull(gesture.takeReleasedAction())
        assertEquals(VoiceHoldPhase.CANCELLED, gesture.state.phase)
    }

    @Test fun aSystemInterruptionWhileTheFileFinishesRevokesThePendingSend() {
        val (gesture, id) = prepared()
        gesture.release(id)
        gesture.cancel(id)
        assertNull(gesture.takeReleasedAction())
    }

    @Test fun callbacksFromAPreviousRecordingCannotAffectANewOne() {
        val (gesture, previousId) = prepared()
        gesture.cancel(previousId)
        gesture.reset()
        val nextId = gesture.begin(180f, 700f, 48f)!!
        gesture.setTarget(VoiceHoldAction.CANCEL, VoiceHoldBounds(20f, 500f, 140f, 600f))
        gesture.setTarget(VoiceHoldAction.TRANSCRIBE, VoiceHoldBounds(220f, 500f, 340f, 600f))
        gesture.cancel(previousId)
        gesture.move(previousId, 80f, 550f)
        gesture.release(previousId)
        assertEquals(VoiceHoldPhase.HOLDING, gesture.state.phase)
        gesture.release(nextId)
        assertEquals(VoiceHoldAction.SEND, gesture.takeReleasedAction())
    }

    @Test fun aSecondPressDoesNotReplaceAnActiveRecording() {
        val (gesture, id) = prepared()
        assertNull(gesture.begin(40f, 700f, 48f))
        gesture.release(id)
        assertEquals(VoiceHoldAction.SEND, gesture.takeReleasedAction())
    }

    @Test fun accessibleActionsUseTheSameReleaseAndDispatchRules() {
        for (action in VoiceHoldAction.entries) {
            val (gesture, id) = prepared()
            gesture.release(id, action)
            assertEquals(action, gesture.takeReleasedAction())
            assertNull(gesture.takeReleasedAction())
            gesture.reset()
            assertNotNull(gesture.begin(180f, 700f, 48f))
        }
    }

    @Test fun releasingBeforeTheOverlayHasLaidOutCannotSend() {
        val gesture = VoiceHoldGesture()
        val id = gesture.begin(180f, 700f, 48f)!!
        gesture.release(id)
        assertEquals(VoiceHoldAction.CANCEL, gesture.takeReleasedAction())
    }

    @Test fun oldTargetLayoutCallbacksCannotEnableANewRecording() {
        val (gesture, oldId) = prepared()
        gesture.reset()
        val newId = gesture.begin(180f, 700f, 48f)!!
        gesture.setTarget(VoiceHoldAction.CANCEL, VoiceHoldBounds(20f, 500f, 140f, 600f), oldId)
        gesture.setTarget(VoiceHoldAction.TRANSCRIBE, VoiceHoldBounds(220f, 500f, 340f, 600f), oldId)
        gesture.release(newId)
        assertEquals(VoiceHoldAction.CANCEL, gesture.takeReleasedAction())
    }

    @Test fun pointerChangesAfterReleaseCannotChangeTheChosenAction() {
        val (gesture, id) = prepared()
        gesture.move(id, 280f, 550f)
        gesture.release(id)
        gesture.move(id, 80f, 550f)
        assertEquals(VoiceHoldAction.TRANSCRIBE, gesture.takeReleasedAction())
    }

    @Test fun cancelSelectionSurvivesSmallBoundaryJitter() {
        val gesture = VoiceHoldGesture()
        val id = gesture.begin(180f, 700f, 48f, 8f)!!
        gesture.setTarget(VoiceHoldAction.CANCEL, VoiceHoldBounds(20f, 500f, 140f, 600f))
        gesture.setTarget(VoiceHoldAction.TRANSCRIBE, VoiceHoldBounds(220f, 500f, 340f, 600f))
        gesture.move(id, 80f, 550f)
        for (x in listOf(139f, 142f, 147f, 141f)) {
            gesture.move(id, x, 550f)
            assertEquals(VoiceHoldAction.CANCEL, gesture.state.action)
        }
        gesture.move(id, 149f, 550f)
        gesture.release(id)
        assertEquals(VoiceHoldAction.SEND, gesture.takeReleasedAction())
    }

    @Test fun transcribeSelectionUsesExitSlopAtTheTopEdge() {
        val gesture = VoiceHoldGesture()
        val id = gesture.begin(180f, 700f, 48f, 8f)!!
        gesture.setTarget(VoiceHoldAction.CANCEL, VoiceHoldBounds(20f, 500f, 140f, 600f))
        gesture.setTarget(VoiceHoldAction.TRANSCRIBE, VoiceHoldBounds(220f, 500f, 340f, 600f))
        gesture.move(id, 280f, 550f)
        gesture.move(id, 280f, 493f)
        assertEquals(VoiceHoldAction.TRANSCRIBE, gesture.state.action)
        gesture.move(id, 280f, 491f)
        gesture.release(id)
        assertEquals(VoiceHoldAction.SEND, gesture.takeReleasedAction())
    }

    @Test fun enteringAnotherTargetOverridesThePreviousExitSlop() {
        val gesture = VoiceHoldGesture()
        val id = gesture.begin(180f, 700f, 48f, 24f)!!
        gesture.setTarget(VoiceHoldAction.CANCEL, VoiceHoldBounds(20f, 500f, 140f, 600f))
        gesture.setTarget(VoiceHoldAction.TRANSCRIBE, VoiceHoldBounds(150f, 500f, 270f, 600f))
        gesture.move(id, 135f, 550f)
        assertEquals(VoiceHoldAction.CANCEL, gesture.state.action)
        gesture.move(id, 155f, 550f)
        gesture.release(id)
        assertEquals(VoiceHoldAction.TRANSCRIBE, gesture.takeReleasedAction())
    }

    @Test fun aNewRecordingReplacesThePreviousExitSlop() {
        val gesture = VoiceHoldGesture()
        val previous = gesture.begin(180f, 700f, 48f, 24f)!!
        gesture.cancel(previous)
        gesture.reset()
        val id = gesture.begin(180f, 700f, 48f)!!
        gesture.setTarget(VoiceHoldAction.CANCEL, VoiceHoldBounds(20f, 500f, 140f, 600f))
        gesture.setTarget(VoiceHoldAction.TRANSCRIBE, VoiceHoldBounds(220f, 500f, 340f, 600f))
        gesture.move(id, 80f, 550f)
        gesture.move(id, 141f, 550f)
        gesture.release(id)
        assertEquals(VoiceHoldAction.SEND, gesture.takeReleasedAction())
    }
}
