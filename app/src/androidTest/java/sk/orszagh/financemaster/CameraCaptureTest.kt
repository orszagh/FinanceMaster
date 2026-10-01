package sk.orszagh.financemaster

import android.Manifest
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraCaptureTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.CAMERA)).around(compose)

    @Test fun cameraPhotoIsSavedAndShownInHistoryAndDetail(): Unit = runBlocking {
        val application = compose.activity.application as FinanceMasterApplication
        val repository = application.repository
        val existing = repository.receipts.first().map { it.id }.toSet()
        compose.onNodeWithText("SCAN RECEIPT").performClick()
        compose.waitUntil(60_000) {
            compose.onAllNodes(hasText("Odfotiť a uložiť") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Odfotiť a uložiť").performClick()
        val saved = withTimeout(60_000) { repository.receipts.first { list -> list.any { it.id !in existing } }.first { it.id !in existing } }
        assertTrue(repository.imageFile(saved).isFile)
        assertTrue(repository.imageFile(saved).length() > 0)
        val image = repository.imageFile(saved)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(image.absolutePath, bounds)
        assertTrue("Camera output must be a readable image", bounds.outWidth > 0 && bounds.outHeight > 0)
        ExifInterface(image).rotationDegrees
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("receipt-${saved.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("História").assertIsDisplayed()
        compose.onNodeWithTag("receipt-${saved.id}").performClick()
        compose.onNodeWithText("Detail účtenky").assertIsDisplayed()
        val description = "Pôvodná fotografia účtenky. Klepnutím zväčšíte."
        compose.waitUntil(60_000) {
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Fotografiu sa nepodarilo načítať.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
        // Virtuálna kamera nemusí obsahovať text; bezpečné uloženie musí fungovať aj vtedy.
    }
}
