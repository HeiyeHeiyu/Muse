package io.zer0.muse.ui.common.feedback

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.window.DialogProperties
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MuseDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dialogRendersFullScreenScrimAndCardContent() {
        composeTestRule.setContent {
            MaterialTheme {
                MuseDialog(
                    onDismissRequest = {},
                    title = "Modal title",
                    content = { androidx.compose.material3.Text("Modal body") },
                    onConfirm = null,
                    dismissText = null,
                )
            }
        }

        composeTestRule.onNodeWithTag(MUSE_DIALOG_SCRIM_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Modal title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Modal body").assertIsDisplayed()
    }

    @Test
    fun scrimTapDoesNotDismissWhenOutsideDismissalIsDisabled() {
        val dismissed = AtomicBoolean(false)
        composeTestRule.setContent {
            MaterialTheme {
                MuseDialog(
                    onDismissRequest = { dismissed.set(true) },
                    title = "Editing memory",
                    content = { androidx.compose.material3.Text("Draft memory") },
                    onConfirm = null,
                    dismissText = null,
                    properties = DialogProperties(
                        dismissOnClickOutside = false,
                        dismissOnBackPress = true,
                        decorFitsSystemWindows = false,
                        usePlatformDefaultWidth = false,
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag(MUSE_DIALOG_SCRIM_TAG).performTouchInput {
            click(Offset.Zero)
        }

        assertFalse("an outside tap must not discard an active edit", dismissed.get())
        composeTestRule.onNodeWithText("Editing memory").assertIsDisplayed()
    }
}
