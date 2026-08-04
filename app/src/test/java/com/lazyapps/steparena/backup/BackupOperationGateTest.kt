package com.lazyapps.steparena.backup

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupOperationGateTest {
    @Test fun awaitIdleCompletesOnlyAfterActiveOperationLeaves() = runBlocking {
        val gate = BackupOperationGate()
        assertTrue(gate.tryEnter())
        val completed = AtomicBoolean(false)
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitIdle()
            completed.set(true)
        }
        assertFalse(completed.get())
        gate.leave()
        waiting.await()
        assertTrue(completed.get())
        assertTrue(gate.tryEnter())
        gate.leave()
    }
}
