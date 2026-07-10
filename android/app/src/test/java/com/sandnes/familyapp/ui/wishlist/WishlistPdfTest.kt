package com.sandnes.familyapp.ui.wishlist

import com.sandnes.familyapp.data.WishModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for the PURE PDF content-shaping helpers (mirrors iOS `WishlistPDFTests`).
 * We assert the content lines and pagination, never pixels — the actual [WishlistPdf.write]
 * rendering needs the Android framework and is exercised on-device.
 */
@RunWith(JUnit4::class)
class WishlistPdfTest {
    private fun wish(
        text: String,
        price: String? = null,
        link: String? = null,
    ): WishModel = WishModel(id = "w-$text", text = text, price = price, link = link)

    // ─── wishlistPdfLines — content order & kinds ─────────────────────────────

    @Test
    fun `lines start with title then subtitle`() {
        val lines = wishlistPdfLines("Birthday", "By Test Nine", emptyList())
        assertEquals(2, lines.size)
        assertEquals(WishPdfLine("Birthday", WishPdfLineKind.TITLE), lines[0])
        assertEquals(WishPdfLine("By Test Nine", WishPdfLineKind.SUBTITLE), lines[1])
    }

    @Test
    fun `blank subtitle is omitted`() {
        val lines = wishlistPdfLines("Birthday", "", emptyList())
        assertEquals(1, lines.size)
        assertEquals(WishPdfLineKind.TITLE, lines[0].kind)
    }

    @Test
    fun `wish renders bullet then price then link`() {
        val lines =
            wishlistPdfLines(
                "Birthday",
                "By Test Nine",
                listOf(wish("AirPods", price = "1990 kr", link = "apple.com/airpods"), wish("Cookbook")),
            )
        assertEquals(
            listOf(
                WishPdfLine("Birthday", WishPdfLineKind.TITLE),
                WishPdfLine("By Test Nine", WishPdfLineKind.SUBTITLE),
                WishPdfLine("•  AirPods", WishPdfLineKind.BULLET),
                WishPdfLine("1990 kr", WishPdfLineKind.META),
                WishPdfLine("apple.com/airpods", WishPdfLineKind.META),
                WishPdfLine("•  Cookbook", WishPdfLineKind.BULLET),
            ),
            lines,
        )
    }

    @Test
    fun `blank wishes are skipped`() {
        val lines = wishlistPdfLines("List", "", listOf(wish("   "), wish("Real gift")))
        assertEquals(1, lines.count { it.kind == WishPdfLineKind.BULLET })
        assertEquals("•  Real gift", lines.first { it.kind == WishPdfLineKind.BULLET }.text)
    }

    @Test
    fun `export never leaks reservation status`() {
        val lines =
            wishlistPdfLines(
                "Gifts",
                "By Alice",
                listOf(wish("Bike", price = "500"), wish("Book", link = "example.com")),
            )
        assertTrue(lines.none { it.text.contains("Reserved", ignoreCase = true) })
        assertTrue(lines.none { it.text.contains("claim", ignoreCase = true) })
    }

    // ─── wishlistPdfBlocks — one block per wish, header first ──────────────────

    @Test
    fun `blocks group each wish and lead with the header`() {
        val blocks = wishlistPdfBlocks("Gifts", "By Alice", listOf(wish("A"), wish("B")))
        assertEquals(3, blocks.size) // header + 2 wishes
        assertEquals(WishPdfLineKind.TITLE, blocks[0].lines.first().kind)
        assertEquals("•  A", blocks[1].lines.first().text)
        assertEquals("•  B", blocks[2].lines.first().text)
    }

    // ─── sanitizedPdfFileName ─────────────────────────────────────────────────

    @Test
    fun `file name uses wishlist name and sanitizes separators`() {
        assertEquals("Mom's - Dad's list.pdf", sanitizedPdfFileName("Mom's / Dad's list"))
    }

    @Test
    fun `blank name falls back to Wishlist`() {
        assertEquals("Wishlist.pdf", sanitizedPdfFileName("   "))
    }

    @Test
    fun `file name trims surrounding whitespace`() {
        assertEquals("Trip.pdf", sanitizedPdfFileName("  Trip  "))
    }

    @Test
    fun `file name strips all path-hostile characters`() {
        assertFalse(sanitizedPdfFileName("a/b\\c:d?e%f*g|h\"i<j>k").contains(Regex("[/\\\\:?%*|\"<>]")))
    }

    // ─── paginateBlockHeights ─────────────────────────────────────────────────

    @Test
    fun `pagination packs blocks that fit onto one page`() {
        val pages = paginateBlockHeights(listOf(100f, 100f), 250f)
        assertEquals(1, pages.size)
        assertEquals(listOf(0, 1), pages[0])
    }

    @Test
    fun `pagination splits a long list across pages without splitting a block`() {
        val pages = paginateBlockHeights(listOf(100f, 100f, 100f, 100f, 100f), 250f)
        assertEquals(3, pages.size)
        assertEquals(listOf(0, 1), pages[0])
        assertEquals(listOf(2, 3), pages[1])
        assertEquals(listOf(4), pages[2])
    }

    @Test
    fun `a block taller than a page still gets its own page`() {
        val pages = paginateBlockHeights(listOf(300f), 250f)
        assertEquals(listOf(listOf(0)), pages)
    }

    @Test
    fun `empty height list paginates to no pages`() {
        assertTrue(paginateBlockHeights(emptyList(), 250f).isEmpty())
    }
}
