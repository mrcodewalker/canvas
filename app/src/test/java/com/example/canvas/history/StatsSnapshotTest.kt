package com.example.canvas.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** The history stacks are changed on one thread while telemetry reads them from another: reading must never throw. */
class StatsSnapshotTest {

    @Test
    fun snapshotOfAnIdleStackIsAnExactCopy() {
        val stack = ArrayDeque<Int>().apply { addAll(listOf(1, 2, 3)) }
        assertEquals(listOf(1, 2, 3), snapshotOf(stack))
        assertEquals(emptyList<Int>(), snapshotOf(ArrayDeque<Int>()))
    }

    @Test
    fun snapshotSurvivesAnotherThreadPushingAndPoppingTheSameStack() {
        val stack = ArrayDeque<Int>()
        val stop = AtomicBoolean(false)
        val writer = thread {
            var i = 0
            while (!stop.get()) {
                stack.addLast(i++)
                stack.addLast(i++)
                if (stack.size > 40) repeat(30) { stack.removeLast() }
            }
        }
        try {
            // the old code iterated the live stack here and threw ConcurrentModificationException within moments
            repeat(200_000) {
                val copy = snapshotOf(stack)
                assertTrue(copy.size <= 60)
            }
        } finally {
            stop.set(true)
            writer.join()
        }
    }
}
