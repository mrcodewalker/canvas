package com.example.canvas.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviorPredictorTest {

    private var now = 1_000_000_000L
    private val clock: () -> Long = { now }
    private fun advanceMs(ms: Long) { now += ms * 1_000_000 }

    @Test
    fun noHistoryMeansEverythingIsPossible() {
        val b = BehaviorPredictor(clock)
        b.recordUndo()
        assertEquals(1.0, b.reachFunction()(5), 1e-9)
    }

    @Test
    fun learnsThatUsersUndoWhatTheyJustDrew() {
        val b = BehaviorPredictor(clock)
        // after drawing 2 strokes: 2 undos ; after drawing 8 strokes: 8 undos
        repeat(6) {
            repeat(2) { b.recordCommit(true, 40_000); advanceMs(200) }
            repeat(2) { b.recordUndo(); advanceMs(50) }
            b.recordBreak()
            repeat(8) { b.recordCommit(true, 40_000); advanceMs(200) }
            repeat(8) { b.recordUndo(); advanceMs(50) }
            b.recordBreak()
        }

        // Situation A: just drew 2 strokes -> expect a short run
        repeat(2) { b.recordCommit(true, 40_000); advanceMs(200) }
        b.recordUndo()
        val shortSituation = b.reachFunction()(4)

        b.recordBreak()
        // Situation B: just drew 8 strokes -> expect a long run
        repeat(8) { b.recordCommit(true, 40_000); advanceMs(200) }
        b.recordUndo()
        val longSituation = b.reachFunction()(4)

        assertTrue("short=$shortSituation long=$longSituation", longSituation > shortSituation)
    }

    @Test
    fun resetForgetsHistory() {
        val b = BehaviorPredictor(clock)
        repeat(5) { b.recordCommit(true, 100); b.recordUndo(); b.recordBreak() }
        b.reset()
        b.recordUndo()
        assertEquals(1.0, b.reachFunction()(10), 1e-9)
    }
}
