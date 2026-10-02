package io.zer0.muse.automation.tools

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.zer0.muse.automation.core.AutomationManager
import io.zer0.muse.automation.executors.ShellExecutor
import io.zer0.muse.automation.vdisplay.VirtualDisplayClient
import io.zer0.muse.automation.vdisplay.VirtualDisplayLeaseRegistry
import io.zer0.muse.automation.vdisplay.VirtualDisplayServerManager
import io.zer0.muse.tools.WorkflowJournal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AutomationWorkflowVirtualDisplayLeaseTest {

    @Test
    fun `virtual close destroys shared display only after final run releases lease`() = runBlocking {
        val directory = Files.createTempDirectory("automation-workflow-display-lease").toFile()
        try {
            val client = mockk<VirtualDisplayClient>()
            val manager = mockk<AutomationManager>(relaxed = true)
            val displayManager = mockk<VirtualDisplayServerManager>(relaxed = true)
            val leases = VirtualDisplayLeaseRegistry()
            coEvery { client.ensureDisplay(720, 1280, 320) } returns
                Result.success(VirtualDisplayClient.DisplayHandle(42, 720, 1280))
            coEvery { client.destroy(42) } returns true

            val first = AutomationWorkflow(
                manager = manager,
                journal = WorkflowJournal(directory),
                virtualDisplayClient = client,
                virtualDisplayManager = displayManager,
                displayLeases = leases,
            )
            val second = AutomationWorkflow(
                manager = manager,
                journal = WorkflowJournal(directory),
                virtualDisplayClient = client,
                virtualDisplayManager = displayManager,
                displayLeases = leases,
            )

            assertFalse(
                first.run(
                    listOf(AutomationWorkflowStep(action = "virtual_ensure")),
                    runId = "run-a",
                ).isError,
            )
            assertFalse(
                second.run(
                    listOf(AutomationWorkflowStep(action = "virtual_ensure")),
                    runId = "run-b",
                ).isError,
            )

            assertTrue(
                first.run(
                    listOf(AutomationWorkflowStep(action = "virtual_close", displayId = 42)),
                    runId = "run-a",
                ).isError.not(),
            )
            coVerify(exactly = 0) { client.destroy(42) }

            assertTrue(
                second.run(
                    listOf(AutomationWorkflowStep(action = "virtual_close", displayId = 42)),
                    runId = "run-b",
                ).isError.not(),
            )
            coVerify(exactly = 1) { client.destroy(42) }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `cached ensure restores lease in a new workflow instance`() = runBlocking {
        val directory = Files.createTempDirectory("automation-workflow-display-resume").toFile()
        try {
            val client = mockk<VirtualDisplayClient>()
            val manager = mockk<AutomationManager>(relaxed = true)
            val displayManager = mockk<VirtualDisplayServerManager>(relaxed = true)
            coEvery { client.ensureDisplay(720, 1280, 320) } returns
                Result.success(VirtualDisplayClient.DisplayHandle(43, 720, 1280))
            coEvery { client.destroy(43) } returns true

            val first = AutomationWorkflow(
                manager = manager,
                journal = WorkflowJournal(directory),
                virtualDisplayClient = client,
                virtualDisplayManager = displayManager,
                displayLeases = VirtualDisplayLeaseRegistry(),
            )
            val firstResult = first.run(
                listOf(AutomationWorkflowStep(action = "virtual_ensure")),
                runId = "resume-display-run",
            )
            assertFalse(firstResult.isError)

            val resumed = AutomationWorkflow(
                manager = manager,
                journal = WorkflowJournal(directory),
                virtualDisplayClient = client,
                virtualDisplayManager = displayManager,
                // New registry simulates a process restart; the journal must rehydrate ownership.
                displayLeases = VirtualDisplayLeaseRegistry(),
            )
            val resumedResult = resumed.run(
                listOf(
                    AutomationWorkflowStep(action = "virtual_ensure"),
                    AutomationWorkflowStep(action = "virtual_close"),
                ),
                runId = "resume-display-run",
            )

            assertFalse(resumedResult.isError)
            coVerify(exactly = 1) { client.ensureDisplay(720, 1280, 320) }
            coVerify(exactly = 1) { client.destroy(43) }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `workflow refreshes compatibility display before input after service restart`() = runBlocking {
        val directory = Files.createTempDirectory("automation-workflow-display-refresh").toFile()
        try {
            val client = mockk<VirtualDisplayClient>()
            val manager = mockk<AutomationManager>(relaxed = true)
            val displayManager = mockk<VirtualDisplayServerManager>(relaxed = true)
            val leases = VirtualDisplayLeaseRegistry()
            coEvery { client.ensureDisplay(720, 1280, 320) } returnsMany listOf(
                Result.success(VirtualDisplayClient.DisplayHandle(42, 720, 1280)),
                Result.success(VirtualDisplayClient.DisplayHandle(43, 720, 1280)),
            )
            coEvery { displayManager.exec("input -d 43 tap 10 20") } returns ShellExecutor.ExecDetail(0, "")

            val workflow = AutomationWorkflow(
                manager = manager,
                journal = WorkflowJournal(directory),
                virtualDisplayClient = client,
                virtualDisplayManager = displayManager,
                displayLeases = leases,
            )

            assertFalse(
                workflow.run(
                    listOf(AutomationWorkflowStep(action = "virtual_ensure")),
                    runId = "refresh-run",
                ).isError,
            )
            val refreshed = workflow.run(
                listOf(
                    AutomationWorkflowStep(action = "virtual_ensure"),
                    AutomationWorkflowStep(action = "virtual_tap", x = 10, y = 20),
                ),
                runId = "refresh-run",
            )

            assertFalse(refreshed.isError)
            coVerify(exactly = 1) { displayManager.exec("input -d 43 tap 10 20") }
        } finally {
            directory.deleteRecursively()
        }
    }
}
