/*
 * Renders a wishlist to a shareable A4 PDF for family members who don't use the app.
 * Owner-facing export: it deliberately shows each wish's name, price, link and image —
 * NEVER reservation/claim status — so the surprise is preserved for the person exporting
 * their own list. Mirrors iOS `WishlistPDF`.
 *
 * The content-shaping logic ([wishlistPdfLines] / [wishlistPdfBlocks] / [paginateBlockHeights] /
 * [formatWishPrice] / [shortenedLink] / [sanitizedPdfFileName]) is pure and unit-tested (assert
 * the content lines, not pixels). The actual `android.graphics.pdf.PdfDocument` rendering in
 * [WishlistPdf.write] is the untested, pixel-producing shell around it. Images are fetched
 * best-effort with a per-image timeout — a failed image never fails the export.
 */
package com.sandnes.familyapp.ui.wishlist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.sandnes.familyapp.data.WishModel
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** The kind of line in the exported PDF — drives its font, colour and indent. */
enum class WishPdfLineKind { TITLE, SUBTITLE, BULLET, META }

/** One rendered line of the PDF. */
data class WishPdfLine(
    val text: String,
    val kind: WishPdfLineKind,
)

/** A group of lines kept together on one page (the header, or a single wish), plus its optional image. */
data class WishPdfBlock(
    val lines: List<WishPdfLine>,
    val imageUrl: String? = null,
)

/** Formats a wish's free-text price for the export: bare numbers become "299 kr" (NOK);
 *  anything else renders exactly as the user typed it. Pure — unit-tested. */
fun formatWishPrice(raw: String): String {
    val trimmed = raw.trim()
    val digitsOnly = trimmed.replace(" ", "")
    return if (digitsOnly.matches(Regex("^\\d+([.,]\\d{1,2})?$"))) "$trimmed kr" else trimmed
}

/** Compact display form of a wish link: scheme + www stripped, ellipsized. Pure — unit-tested. */
fun shortenedLink(
    url: String,
    maxLength: Int = 60,
): String {
    val stripped =
        url
            .trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removeSuffix("/")
    return if (stripped.length <= maxLength) stripped else stripped.take(maxLength - 1) + "…"
}

/**
 * The ordered content blocks of the export: block 0 is the header (title + optional subtitle),
 * then one block per non-blank wish (name + optional price/link lines + optional image).
 * Reservation status is never included. Pure — unit-tested.
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
                val lines = mutableListOf(WishPdfLine(wish.text, WishPdfLineKind.BULLET))
                wish.price?.takeIf { it.isNotBlank() }?.let { lines += WishPdfLine(formatWishPrice(it), WishPdfLineKind.META) }
                wish.link?.takeIf { it.isNotBlank() }?.let { lines += WishPdfLine(shortenedLink(it), WishPdfLineKind.META) }
                WishPdfBlock(lines, imageUrl = wish.imageUrl?.takeIf { it.isNotBlank() })
            }

    return listOf(WishPdfBlock(header)) + wishBlocks
}

/** Flattened content lines of the export (title, subtitle, wishes). Pure — unit-tested. */
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

    private const val META_INDENT = 0f
    private const val LINE_GAP = 3f // between lines within a block
    private const val BLOCK_GAP = 16f // after each wish block

    // Wish image thumbnail (points) and its gap to the text column.
    private const val IMAGE_SIZE = 90f
    private const val IMAGE_TEXT_GAP = 14f
    private const val IMAGE_CORNER_RADIUS = 10f
    private const val IMAGE_FETCH_PX = 360
    private const val IMAGE_FETCH_TIMEOUT_MS = 10_000L

    // Header accent rule under the title block (Heirloom Indigo).
    private const val ACCENT_COLOR = 0xFF5457E8.toInt()
    private const val ACCENT_HEIGHT = 3f
    private const val ACCENT_WIDTH = 64f
    private const val ACCENT_GAP = 10f

    // Fixed inks — the page is always white, so we must not use theme colours.
    private const val INK_COLOR = 0xFF000000.toInt()
    private const val META_COLOR = 0xFF525252.toInt() // grey ~ white 0.32

    private const val CACHE_SUBDIR = "wishlist_pdfs"

    /**
     * Writes the wishlist to a PDF in the app cache and returns the file (null on failure).
     * Suspends to download wish images (best-effort, per-image timeout). Call off the main
     * thread; share via [android.content.Intent.ACTION_SEND] through the app's FileProvider.
     */
    suspend fun write(
        context: Context,
        name: String,
        subtitle: String,
        wishes: List<WishModel>,
    ): File? =
        runCatching {
            val blocks = wishlistPdfBlocks(name, subtitle, wishes)
            val images = fetchImages(context, blocks)

            val contentWidth = PAGE_WIDTH - MARGIN * 2
            val heights = blocks.mapIndexed { index, block -> blockHeight(block, index in images.keys, contentWidth) }
            val pages = paginateBlockHeights(heights, CONTENT_HEIGHT)

            val document = PdfDocument()
            pages.forEachIndexed { pageIndex, blockIndices ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                var cursorY = MARGIN.toFloat()
                blockIndices.forEach { blockIndex ->
                    cursorY = drawBlock(page.canvas, blocks[blockIndex], images[blockIndex], cursorY, contentWidth)
                    if (blockIndex == 0) cursorY = drawAccentRule(page.canvas, cursorY)
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

    /** Downloads each block's image (software bitmap — PDF canvases can't draw hardware ones).
     *  Failures and timeouts are silently skipped so the export always succeeds. */
    private suspend fun fetchImages(
        context: Context,
        blocks: List<WishPdfBlock>,
    ): Map<Int, Bitmap> {
        val loader = SingletonImageLoader.get(context)
        val result = mutableMapOf<Int, Bitmap>()
        blocks.forEachIndexed { index, block ->
            val url = block.imageUrl ?: return@forEachIndexed
            val bitmap =
                runCatching {
                    withTimeoutOrNull(IMAGE_FETCH_TIMEOUT_MS) {
                        val request =
                            ImageRequest
                                .Builder(context)
                                .data(url)
                                .size(IMAGE_FETCH_PX, IMAGE_FETCH_PX)
                                .allowHardware(false)
                                .build()
                        (loader.execute(request) as? SuccessResult)?.image?.toBitmap()
                    }
                }.getOrNull()
            if (bitmap != null) result[index] = bitmap
        }
        return result
    }

    /** Draws one block (optional thumbnail left, text right) starting at [top]; returns the Y just below it. */
    private fun drawBlock(
        canvas: Canvas,
        block: WishPdfBlock,
        image: Bitmap?,
        top: Float,
        contentWidth: Int,
    ): Float {
        val textIndent = if (image != null) IMAGE_SIZE + IMAGE_TEXT_GAP else 0f
        val textWidth = (contentWidth - textIndent).toInt().coerceAtLeast(1)

        if (image != null) {
            drawRoundedImage(canvas, image, MARGIN.toFloat(), top)
        }

        var cursorY = top
        block.lines.forEachIndexed { index, line ->
            if (index > 0) cursorY += LINE_GAP
            cursorY += drawLine(canvas, line, cursorY, textIndent, textWidth)
        }
        // The block occupies at least the thumbnail's height.
        return if (image != null) maxOf(cursorY, top + IMAGE_SIZE) else cursorY
    }

    /** Center-crops [bitmap] into a rounded IMAGE_SIZE square at ([left], [top]). */
    private fun drawRoundedImage(
        canvas: Canvas,
        bitmap: Bitmap,
        left: Float,
        top: Float,
    ) {
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = IMAGE_SIZE / minOf(bitmap.width, bitmap.height).toFloat()
        val matrix =
            Matrix().apply {
                setScale(scale, scale)
                postTranslate(
                    left - (bitmap.width * scale - IMAGE_SIZE) / 2f,
                    top - (bitmap.height * scale - IMAGE_SIZE) / 2f,
                )
            }
        shader.setLocalMatrix(matrix)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        val rect = RectF(left, top, left + IMAGE_SIZE, top + IMAGE_SIZE)
        canvas.drawRoundRect(rect, IMAGE_CORNER_RADIUS, IMAGE_CORNER_RADIUS, paint)
    }

    /** Short Heirloom-Indigo rule under the header; returns the Y just below it. */
    private fun drawAccentRule(
        canvas: Canvas,
        top: Float,
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT_COLOR }
        val y = top + ACCENT_GAP
        canvas.drawRoundRect(
            RectF(MARGIN.toFloat(), y, MARGIN + ACCENT_WIDTH, y + ACCENT_HEIGHT),
            ACCENT_HEIGHT / 2f,
            ACCENT_HEIGHT / 2f,
            paint,
        )
        return y + ACCENT_HEIGHT
    }

    /** Draws one wrapped line at [top]; returns the height it consumed. */
    private fun drawLine(
        canvas: Canvas,
        line: WishPdfLine,
        top: Float,
        indent: Float,
        width: Int,
    ): Float {
        val layout = layoutFor(line, width)
        canvas.save()
        canvas.translate(MARGIN + indent + if (line.kind == WishPdfLineKind.META) META_INDENT else 0f, top)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    /** Measures a block's total height (used for pagination). */
    private fun blockHeight(
        block: WishPdfBlock,
        hasImage: Boolean,
        contentWidth: Int,
    ): Float {
        val textIndent = if (hasImage) IMAGE_SIZE + IMAGE_TEXT_GAP else 0f
        val width = (contentWidth - textIndent).toInt().coerceAtLeast(1)
        var height = 0f
        block.lines.forEachIndexed { index, line ->
            if (index > 0) height += LINE_GAP
            height += layoutFor(line, width).height
        }
        val accent = if (block.lines.firstOrNull()?.kind == WishPdfLineKind.TITLE) ACCENT_GAP + ACCENT_HEIGHT else 0f
        return maxOf(height, if (hasImage) IMAGE_SIZE else 0f) + accent
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
