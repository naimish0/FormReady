package com.rameshta.formready.feature.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ScannerPageOrderingTest {
    @Test
    fun movesPageWithoutDroppingOrDuplicatingItems() {
        assertEquals(
            listOf("second", "first", "third"),
            ScannerPageOrdering.move(listOf("first", "second", "third"), 0, 1),
        )
    }

    @Test
    fun invalidMoveKeepsOriginalList() {
        val pages = listOf("first", "second")

        assertSame(pages, ScannerPageOrdering.move(pages, 0, -1))
        assertSame(pages, ScannerPageOrdering.move(pages, 1, 1))
    }
}
