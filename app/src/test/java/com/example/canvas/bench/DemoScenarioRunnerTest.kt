package com.example.canvas.bench

import com.example.canvas.core.Stroke
import com.example.canvas.history.HistoryMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoScenarioRunnerTest {

    @Test
    fun testDemoExecutesAllPhases() = runTest {
        val modesSwitched = mutableListOf<HistoryMode>()
        val strokesDrawn = mutableListOf<Stroke>()
        var stressCount = 0
        var undoCount = 0
        var clearCount = 0
        var finalState: DemoState? = null

        val runner = DemoScenarioRunner(
            timeScale = 50_000f, // Ultra-fast acceleration for testing
            dispatcher = Dispatchers.Unconfined,
            onStateUpdate = { state ->
                finalState = state
            },
            onSwitchMode = { mode ->
                modesSwitched.add(mode)
            },
            onDrawStroke = { stroke ->
                strokesDrawn.add(stroke)
            },
            onUndo = {
                undoCount++
            },
            onStress = {
                stressCount++
            },
            onClearCanvas = {
                clearCount++
            }
        )

        runner.start(this)
        runner.awaitCompletion()

        // Verify execution results
        assertTrue("Modes should be switched", modesSwitched.contains(HistoryMode.NAIVE))
        assertTrue("Modes should include DIRTY_RECT", modesSwitched.contains(HistoryMode.DIRTY_RECT))
        assertTrue("Modes should include TIERED", modesSwitched.contains(HistoryMode.TIERED))
        assertEquals(3, modesSwitched.size)

        assertEquals("Should clear canvas on each phase", 3, clearCount)
        assertTrue("Strokes should be drawn (8 naive + 16 dirty + 20 tiered = 44)", strokesDrawn.size >= 40)
        assertTrue("Memory stress should be triggered at least once", stressCount >= 1)
        assertTrue("Undo should be executed 15 times", undoCount >= 15)

        assertFalse(runner.isDemoRunning)
        assertEquals(false, finalState?.isRunning)
        assertEquals(180, finalState?.totalSeconds)
    }

    @Test
    fun testStopCancelsDemo() = runTest {
        val runner = DemoScenarioRunner(
            timeScale = 1.0f, // Normal scale
            dispatcher = Dispatchers.Default
        )

        assertFalse(runner.isDemoRunning)
        runner.start(this)
        assertTrue(runner.isDemoRunning)

        runner.stop()
        assertFalse(runner.isDemoRunning)
    }
}
