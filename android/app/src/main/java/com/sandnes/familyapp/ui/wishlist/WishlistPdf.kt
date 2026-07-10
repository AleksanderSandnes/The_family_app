/*
 * Renders a wishlist to a shareable A4 PDF for family members who don't use the app.
 * Owner-facing export: it deliberately shows only each wish's name, price and link —
 * NEVER reservation/claim status — so the surprise is preserved for the person exporting
 * their own list. Mirrors iOS `WishlistPDF`.
 *
 * The content-shaping logic ([wishlistPdfLines] / [wishlistPdfBlocks] / [paginateBlockHeights] /
 * [sanitizedPdfFileName]) is pure and unit-tested (assert the content lines, not pixels). The
 * actual `android.graphics.pdf.PdfDocument` rendering in [WishlistPdf.write] is the untested,
 * pixel-producing shell around it.
 */
package com.sandnes.familyapp.ui.wishlist

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import com.sandnes.familyapp.data.WishModel
import java.io.File

/** The kind of line in the exported PDF — drives its font, colour and indent. */
enum class WishPdfLineKind { TITLE, SUBTITLE, BULLET, META }

/** One rendered line of the PDF. */
data class WishPdfLine(
    val text: String,
    val kind: WishPdfLineKind,
)

/** A group of lines kept together on one page (the header, or a single wish + its price/link). */
data class WishPdfBlock(
    val lines: List<WishPdfLine>,
)

/**
 * The ordered content blocks of the export: block 0 is the header (title + optional subtitle),
 * then one block per non-blank wish (bullet + optional price + optional link). Reservation status
 * is never included. Pure — unit-tested.
 */
fun wishlistPdfBlocks(
    name: String,
    subtitle: String,
    wishes: List<WishModel>,
): List<WishPdfBlock> {
    val header = mutableListOf(WishPdfLine(name, WishPdfLineKind.TITLE))
    if (subtitle.isNotBlank()) header += WishPdfLine(subtitle, WishPdfLineKind.SUBTITLE)

    val wishBlocks =
        wishes
            .filter { it.text.isNotBlank() }
            .map { wish ->
                val lines = mutableListOf(WishPdfLine("•  ${wish.text}", WishPdfLineKind.BULLET))
                wish.price?.takeIf { it.isNotBlank() }?.let { lines += WishPdfLine(it, WishPdfLineKind.META) }
                wish.link?.takeIf { it.isNotBlank() }?.let { lines += WishPdfLine(it, WishPdfLineKind.META) }
                WishPdfBlock(lines)
            }

    return listOf(WishPdfBlock(header)) + wishBlocks
}

/** Flattened content lines of the export (title, subtitle, bulleted wishes). Pure — unit-tested. */
fun wishlistPdfLines(
    name: String,
    subtitle: String,
    wishes: List<WishModel>,
): List<WishPdfLine> = wishlistPdfBlocks(name, subtitle, wishes).flatMap { it.lines }

/**
 * Greedy pagination: packs [heights] (one per block) onto pages of [contentHeight], never
 * splitting a block. Returns each page as the list of block indices it holds. Pure — unit-tested.
 */
fun paginateBlockHeights(
    heights: List<Float>,
    contentHeight: Float,
): List<List<Int>> {
    val pages = mutableListOf<MutableList<Int>>()
    var current = mutableListOf<Int>()
    var cursor = 0f
    heights.forEachIndexed { index, height ->
        if (current.isNotEmpty() && cursor + height > contentHeight) {
            pages += current
            current = mutableListOf()
            cursor = 0f
        }
        current += index
        cursor += height
    }
    if (current.isNotEmpty()) pages += current
    return pages
}

/** Filesystem-safe PDF file name from a wishlist name. Mirrors iOS `fileName(for:)`. Pure. */
fun sanitizedPdfFileName(name: String): String {
    val base = name.trim()
    val safe = base.ifEmpty { "Wishlist" }
    val separators = "/\\:?%*|\"<>"
    val cleaned = safe.map { if (it in separators) '-' else it }.joinToString("")
    return "$cleaned.pdf"
}

/** Renders and writes wishlist PDFs. The pure helpers above do the content shaping. */
object WishlistPdf {
    // A4 at 72 dpi, in points, matching iOS.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48
    private const val CONTENT_HEIGHT = (PAGE_HEIGHT - MARGIN * 2).toFloat()

    private const val TITLE_TEXT_SIZE = 26f
    private const val SUBTITLE_TEXT_SIZE = 13f
    private const val BULLET_TEXT_SIZE = 15f
    private const val META_TEXT_SIZE = 12f

    private const val META_INDENT = 18f
    private const val LINE_GAP = 3f // between lines within a block
    private const val BLOCK_GAP = 12f // after each wish block

    // Fixed inks — the page is always white, so we must not use theme colours.
    private const val INK_COLOR = 0xFF000000.toInt()
    private const val META_COLOR = 0xFF525252.toInt() // grey ~ white 0.32

    private const val CACHE_SUBDIR = "wishlist_pdfs"

    /**
     * Writes the wishlist to a PDF in the app cache and returns the file (null on failure).
     * Share it with [android.content.Intent.ACTION_SEND] via the app's FileProvider.
     */
    fun write(
        context: Context,
        name: String,
        subtitle: String,
        wishes: List<WishModel>,
    ): File? =
        runCatching {
            val contentWidth = PAGE_WIDTH - MARGIN * 2
            val blocks = wishlistPdfBlocks(name, subtitle, wishes)
            val heights = blocks.map { blockHeight(it, contentWidth) }
            val pages = paginateBlockHeights(heights, CONTENT_HEIGHT)

            val document = PdfDocument()
            pages.forEachIndexed { pageIndex, blockIndices ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                var cursorY = MARGIN.toFloat()
                blockIndices.forEach { blockIndex ->
                    cursorY = drawBlock(page.canvas, blocks[blockIndex], cursorY, contentWidth)
                    cursorY += BLOCK_GAP
                }
                document.finishPage(page)
            }

            val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
            val file = File(dir, sanitizedPdfFileName(name))
            file.outputStream().use { document.writeTo(it) }
            document.close()
            file
        }.getOrNull()

    /** Draws one block starting at [top]; returns the Y just below it. */
    private fun drawBlock(
        canvas: Canvas,
        block: WishPdfBlock,
        top: Float,
        contentWidth: Int,
    ): Float {
        var cursorY = top
        block.lines.forEachIndexed { index, line ->
            if (index > 0) cursorY += LINE_GAP
            cursorY += drawLine(canvas, line, cursorY, contentWidth)
        }
        return cursorY
    }

    /** Draws one wrapped line at [top]; returns the height it consumed. */
    private fun drawLine(
        canvas: Canvas,
        line: WishPdfLine,
        top: Float,
        contentWidth: Int,
    ): Float {
        val indent = if (line.kind == WishPdfLineKind.META) META_INDENT else 0f
        val layout = layoutFor(line, (contentWidth - indent).toInt().coerceAtLeast(1))
        canvas.save()
        canvas.translate(MARGIN + indent, top)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    /** Measures a block's total height (used for pagination). */
    private fun blockHeight(
        block: WishPdfBlock,
        contentWidth: Int,
    ): Float {
        var height = 0f
        block.lines.forEachIndexed { index, line ->
            if (index > 0) height += LINE_GAP
            val indent = if (line.kind == WishPdfLineKind.META) META_INDENT else 0f
            height += layoutFor(line, (contentWidth - indent).toInt().coerceAtLeast(1)).height
        }
        return height
    }

    private fun layoutFor(
        line: WishPdfLine,
        width: Int,
    ): StaticLayout {
        val paint = paintFor(line.kind)
        return StaticLayout.Builder.obtain(line.text, 0, line.text.length, paint, width).build()
    }

    private fun paintFor(kind: WishPdfLineKind): TextPaint =
        TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            when (kind) {
                WishPdfLineKind.TITLE -> {
                    textSize = TITLE_TEXT_SIZE
                    color = INK_COLOR
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                WishPdfLineKind.SUBTITLE -> {
                    textSize = SUBTITLE_TEXT_SIZE
                    color = META_COLOR
                    typeface = Typeface.DEFAULT
                }
                WishPdfLineKind.BULLET -> {
                    textSize = BULLET_TEXT_SIZE
                    color = INK_COLOR
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                WishPdfLineKind.META -> {
                    textSize = META_TEXT_SIZE
                    color = META_COLOR
                    typeface = Typeface.DEFAULT
                }
            }
        }
}
