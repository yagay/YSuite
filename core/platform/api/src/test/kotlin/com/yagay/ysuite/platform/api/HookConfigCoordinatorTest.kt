package com.yagay.ysuite.platform.api

import com.yagay.ysuite.common.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HookConfigCoordinatorTest {
    private class Fake : HookGateway {
        val operations = mutableListOf<String>()
        var failAt: String? = null
        override suspend fun status() = CapabilityStatus.Available
        override suspend fun writeConfig(group: String, key: String, value: String?): Outcome<Unit> {
            operations += "write:" + key
            return if (failAt == key) Outcome.Failure(
                code = "write_failed", message = "write failed",
            ) else Outcome.Success(Unit)
        }
        override suspend fun reload(scopePackages: Set<String>): Outcome<Unit> {
            operations += "scope:" + scopePackages.sorted().joinToString(",")
            return if (failAt == "scope") Outcome.Failure(
                code = "scope_failed", message = "scope denied",
            ) else Outcome.Success(Unit)
        }
    }

    @Test fun revisionIsStableAcrossMapOrderAndChangesWithValues() {
        val first = linkedMapOf("one" to "1", "two" to null)
        val reversed = linkedMapOf("two" to null, "one" to "1")
        assertEquals(
            HookConfigCoordinator.revisionFor(first),
            HookConfigCoordinator.revisionFor(reversed),
        )
        assertNotEquals(
            HookConfigCoordinator.revisionFor(first),
            HookConfigCoordinator.revisionFor(mapOf("one" to "2", "two" to null)),
        )
    }

    @Test fun successfulScopeDoesNotClaimRuntimeActivation() = runBlocking {
        val fake = Fake()
        val result = HookConfigCoordinator(fake).publish(
            group = "test", values = linkedMapOf("first" to "A", "second" to "B"),
            scopePackages = setOf("app.example"),
        )
        assertTrue(result is Outcome.Success)
        val receipt = (result as Outcome.Success).value
        assertFalse(receipt.runtimeVerified)
        assertEquals(HookDeliveryPhase.AwaitingTargetRuntime, receipt.phase)
        assertEquals(
            listOf("write:first", "write:second", "write:__config_revision", "scope:app.example"),
            fake.operations,
        )
    }

    @Test fun failedWriteDoesNotAdvanceRevisionOrRequestScope() = runBlocking {
        val fake = Fake().apply { failAt = "second" }
        val result = HookConfigCoordinator(fake).publish(
            "test", linkedMapOf("first" to "A", "second" to "B"), setOf("app.example"),
        )
        assertEquals("write_failed", (result as Outcome.Failure).error.code)
        assertEquals(listOf("write:first", "write:second"), fake.operations)
    }

    @Test fun failedScopeCannotBeReportedAsSuccess() = runBlocking {
        val fake = Fake().apply { failAt = "scope" }
        val result = HookConfigCoordinator(fake).publish(
            "test", mapOf("first" to "A"), setOf("app.example"),
        )
        assertEquals("scope_failed", (result as Outcome.Failure).error.code)
        assertEquals(
            listOf("write:first", "write:__config_revision", "scope:app.example"),
            fake.operations,
        )
    }

    @Test fun invalidReservedKeyDoesNotWriteAnything() = runBlocking {
        val fake = Fake()
        val result = HookConfigCoordinator(fake).publish(
            "test", mapOf(HookConfigCoordinator.REVISION_KEY to "fake"),
        )
        assertEquals("hook_publication_invalid", (result as Outcome.Failure).error.code)
        assertTrue(fake.operations.isEmpty())
    }

    @Test fun perPackageRevisionsCannotOverwriteEachOther() = runBlocking {
        val fake = Fake()
        val coordinator = HookConfigCoordinator(fake)
        val first = coordinator.publish(
            "yparam", mapOf("app.first" to "A"),
            revisionKey = HookConfigCoordinator.revisionKeyFor("app.first"),
        )
        val second = coordinator.publish(
            "yparam", mapOf("app.second" to "B"),
            revisionKey = HookConfigCoordinator.revisionKeyFor("app.second"),
        )
        assertTrue(first is Outcome.Success)
        assertTrue(second is Outcome.Success)
        assertEquals(
            listOf(
                "write:app.first", "write:__config_revision:app.first",
                "write:app.second", "write:__config_revision:app.second",
            ), fake.operations,
        )
    }

    @Test fun commandCompletionDifferentiatesTransportAndExecution() {
        assertEquals(CommandCompletion.Succeeded, RootResult(0, "", "").completion)
        assertEquals(CommandCompletion.NonZeroExit, RootResult(1, "", "").completion)
        assertEquals(CommandCompletion.TimedOut, RootResult(124, "", "").completion)
    }
}
