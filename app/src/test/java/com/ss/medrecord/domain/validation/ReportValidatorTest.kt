package com.ss.medrecord.domain.validation

import com.ss.medrecord.core.common.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 2 MB rule is the one business rule in this app that refuses a user's
 * data outright, so its boundaries are pinned down rather than assumed.
 */
class ReportValidatorTest {

    private val cap = AppConstants.MAX_REPORT_FILE_SIZE_BYTES

    @Test
    fun `a file exactly on the cap is accepted`() {
        // "capped at 2 MB" reads as inclusive, and an off-by-one here would
        // reject a file the message says is allowed.
        assertTrue(ReportValidator.isWithinSizeLimit(cap))
        assertTrue(ReportValidator.validateSize(cap).isValid)
        assertFalse(ReportValidator.isWithinSizeLimit(cap + 1))
    }

    @Test
    fun `an empty file is rejected before the size rule`() {
        val result = ReportValidator.validateSize(0)

        assertFalse(result.isValid)
        assertEquals("That file is empty", result.errorOrNull)
    }

    @Test
    fun `the rejection message names both the limit and the actual size`() {
        val result = ReportValidator.validateSize(3L * 1024 * 1024)

        assertFalse(result.isValid)
        val message = requireNotNull(result.errorOrNull)
        assertTrue(message.contains("2.0 MB"))
        assertTrue(message.contains("3.0 MB"))
    }

    @Test
    fun `only renderable file types are accepted`() {
        assertTrue(ReportValidator.validateFileType("application/pdf").isValid)
        assertTrue(ReportValidator.validateFileType("image/jpeg").isValid)
        assertTrue(ReportValidator.validateFileType("image/heic").isValid)

        assertFalse(ReportValidator.validateFileType("video/mp4").isValid)
        assertFalse(ReportValidator.validateFileType("application/zip").isValid)
        // Content resolvers do return null for a type they cannot determine.
        assertFalse(ReportValidator.validateFileType(null).isValid)
    }

    @Test
    fun `file names are capped and cannot be blank`() {
        assertFalse(ReportValidator.validateFileName("  ").isValid)
        assertTrue(ReportValidator.validateFileName("blood-panel.pdf").isValid)
        assertFalse(
            ReportValidator.validateFileName(
                "x".repeat(ReportValidator.MAX_FILE_NAME_LENGTH + 1),
            ).isValid,
        )
    }

    @Test
    fun `sanitising strips path components from a supplied name`() {
        // The name comes from another app via a content Uri, so it is input.
        assertEquals("passwd", ReportValidator.sanitiseFileName("../../etc/passwd"))
        assertEquals("report.pdf", ReportValidator.sanitiseFileName("C:\\temp\\report.pdf"))
        assertEquals("scan.jpg", ReportValidator.sanitiseFileName("  scan.jpg  "))
    }

    @Test
    fun `sanitising never returns an empty name`() {
        // A blank name would leave a tile with nothing to label it.
        assertEquals("report", ReportValidator.sanitiseFileName("   "))
        assertEquals("report", ReportValidator.sanitiseFileName("/"))
    }

    @Test
    fun `sanitising truncates rather than rejecting`() {
        val long = "a".repeat(500)

        assertEquals(
            ReportValidator.MAX_FILE_NAME_LENGTH,
            ReportValidator.sanitiseFileName(long).length,
        )
    }
}
