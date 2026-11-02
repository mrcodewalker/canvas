package com.example.canvas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialSetupTest {

    @Test
    fun testCoroutinesExecution() = runTest {
        val result = withContext(Dispatchers.Default) {
            val a = 2048
            val b = 2048
            val bytesPerPixel = 4
            a * b * bytesPerPixel // 16 MiB
        }
        assertEquals(16 * 1024 * 1024, result)
    }

    @Test
    fun testBasicEnvironment() {
        assertTrue("Initial test setup is active", true)
    }
}
