/*
 * Renders a wishlist to a shareable A4 PDF for family members who don't use the app.
 * Owner-facing export: it deliberately shows each wish's name, description, price, link
 * and image — NEVER reservation/claim status — so the surprise is preserved for the person
 * exporting their own list. Mirrors iOS `WishlistPDF`.
 *
 * Visual language follows the app's light mode (The Glass House): ambient canvas, white
 * rounded cards with a hairline border, the brand gradient accent under the header, and
 * token ink colours. Colours are FIXED (not theme-resolved) — the export is always "light".
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
import android.graphics.LinearGradient
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

/** The kind of line in the exported PDF — drives its font, colour and spacing. */
enum class WishPdfLineKind { TITLE, SUBTITLE, BULLET, DESCRIPTION, PRICE, LINK }

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
 * then one block per non-blank wish (name + optional description/price/link lines + optional
 * image). Reservation status is never included. Pure — unit-tested.
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
                wish.description?.takeIf { it.isNotBlank() }?.let { lines += WishPdfLine(it, WishPdfLineKind.DESCRIPTION) }
                wish.price?.takeIf { it.isNotBlank() }?.let { lines += WishPdfLine(formatWishPrice(it), WishPdfLineKind.PRICE) }
                wish.link?.takeIf { it.isNotBlank() }?.let { lines += WishPdfLine(shortenedLink(it), WishPdfLineKind.LINK) }
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
    private const val MARGIN = 44

    private const val TITLE_TEXT_SIZE = 26f
    private const val SUBTITLE_TEXT_SIZE = 13f
    private const val NAME_TEXT_SIZE = 15f
    private const val BODY_TEXT_SIZE = 12f
    private const val FOOTER_TEXT_SIZE = 9f

    private const val LINE_GAP = 4f // between lines within a block
    private const val BLOCK_GAP = 12f // between wish cards

    // Wish card chrome (Glass House light mode, in points).
    private const val CARD_PADDING = 12f
    private const val CARD_CORNER_RADIUS = 14f
    private const val CARD_BORDER_WIDTH = 1f

    // Wish image thumbnail (points) and its gap to the text column.
    private const val IMAGE_SIZE = 84f
    private const val IMAGE_TEXT_GAP = 14f
    private const val IMAGE_FETCH_PX = 360
    private const val IMAGE_FETCH_TIMEOUT_MS = 10_000L

    // Header accent rule: the one sanctioned brand gradient (Heirloom Indigo → violet).
    private const val ACCENT_START = 0xFF5457E8.toInt()
    private const val ACCENT_END = 0xFF7C3AED.toInt()
    private const val ACCENT_HEIGHT = 3f
    private const val ACCENT_WIDTH = 72f
    private const val ACCENT_GAP = 10f

    // Fixed Glass House light-mode inks — never theme-resolved.
    private const val CANVAS_COLOR = 0xFFEFF1F8.toInt() // ambient base
    private const val CARD_COLOR = 0xFFFFFFFF.toInt()
    private const val CARD_BORDER_COLOR = 0x1A16192A // ink at 10 %
    private const val INK_COLOR = 0xFF16192A.toInt() // ink
    private const val SECONDARY_COLOR = 0xFF5F6780.toInt() // text secondary
    private const val ACCENT_INK = 0xFF4F55E6.toInt() // interactive accent
    private const val CAPTION_COLOR = 0xFF8B92AC.toInt() // caption

    private const val FOOTER_TEXT = "The Family App  ·  thefamilyapp.app"

    private const val CONTENT_HEIGHT = (PAGE_HEIGHT - MARGIN * 2 - 24).toFloat() // 24 = footer strip

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
                page.canvas.drawColor(CANVAS_COLOR)
                var cursorY = MARGIN.toFloat()
                blockIndices.forEach { blockIndex ->
                    cursorY =
                        if (blockIndex == 0) {
                            drawHeader(page.canvas, blocks[blockIndex], cursorY, contentWidth)
                        } else {
                            drawWishCard(page.canvas, blocks[blockIndex], images[blockIndex], cursorY, contentWidth)
                        }
                    cursorY += BLOCK_GAP
                }
                drawFooter(page.canvas)
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

    /** Title + subtitle over the brand-gradient accent rule; returns the Y just below. */
    private fun drawHeader(
        canvas: Canvas,
        block: WishPdfBlock,
        top: Float,
        contentWidth: Int,
    ): Float {
        var cursorY = top
        block.lines.forEachIndexed { index, line ->
            if (index > 0) cursorY += LINE_GAP
            cursorY += drawLine(canvas, line, MARGIN.toFloat(), cursorY, contentWidth)
        }
        val y = cursorY + ACCENT_GAP
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    LinearGradient(
                        MARGIN.toFloat(),
                        0f,
                        MARGIN + ACCENT_WIDTH,
                        0f,
                        ACCENT_START,
                        ACCENT_END,
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRoundRect(
            RectF(MARGIN.toFloat(), y, MARGIN + ACCENT_WIDTH, y + ACCENT_HEIGHT),
            ACCENT_HEIGHT / 2f,
            ACCENT_HEIGHT / 2f,
            paint,
        )
        return y + ACCENT_HEIGHT
    }

    /** One wish as a white rounded card with hairline border, image left, text right. */
    private fun drawWishCard(
        canvas: Canvas,
        block: WishPdfBlock,
        image: Bitmap?,
        top: Float,
        contentWidth: Int,
    ): Float {
        val cardHeight = blockHeight(block, image != null, contentWidth)
        val card = RectF(MARGIN.toFloat(), top, (MARGIN + contentWidth).toFloat(), top + cardHeight)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD_COLOR }
        canvas.drawRoundRect(card, CARD_CORNER_RADIUS, CARD_CORNER_RADIUS, fill)
        val border =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = CARD_BORDER_COLOR
                style = Paint.Style.STROKE
                strokeWidth = CARD_BORDER_WIDTH
            }
        canvas.drawRoundRect(card, CARD_CORNER_RADIUS, CARD_CORNER_RADIUS, border)

        val innerLeft = MARGIN + CARD_PADDING
        val innerTop = top + CARD_PADDING
        if (image != null) {
            drawImage(canvas, image, innerLeft, innerTop)
        }
        val textLeft = innerLeft + if (image != null) IMAGE_SIZE + IMAGE_TEXT_GAP else 0f
        val textWidth = (card.right - CARD_PADDING - textLeft).toInt().coerceAtLeast(1)

        var cursorY = innerTop
        block.lines.forEachIndexed { index, line ->
            if (index > 0) cursorY += LINE_GAP
            cursorY += drawLine(canvas, line, textLeft, cursorY, textWidth)
        }
        return top + cardHeight
    }

    /** Center-crops [bitmap] into an IMAGE_SIZE square at ([left], [top]). */
    private fun drawImage(
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
        canvas.drawRect(RectF(left, top, left + IMAGE_SIZE, top + IMAGE_SIZE), paint)
    }

    /** Centered caption at the bottom of every page. */
    private fun drawFooter(canvas: Canvas) {
        val paint =
            TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                textSize = FOOTER_TEXT_SIZE
                color = CAPTION_COLOR
                textAlign = Paint.Align.CENTER
            }
        canvas.drawText(FOOTER_TEXT, PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN / 2f, paint)
    }

    /** Draws one wrapped line at ([left], [top]); returns the height it consumed. */
    private fun drawLine(
        canvas: Canvas,
        line: WishPdfLine,
        left: Float,
        top: Float,
        width: Int,
    ): Float {
        val layout = layoutFor(line, width)
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    /** Measures a block's total height, card chrome included (used for pagination). */
    private fun blockHeight(
        block: WishPdfBlock,
        hasImage: Boolean,
        contentWidth: Int,
    ): Float {
        val isHeader = block.lines.firstOrNull()?.kind == WishPdfLineKind.TITLE
        val textIndent = if (hasImage) IMAGE_SIZE + IMAGE_TEXT_GAP else 0f
        val padding = if (isHeader) 0f else CARD_PADDING * 2
        val width = (contentWidth - textIndent - padding).toInt().coerceAtLeast(1)
        var height = 0f
        block.lines.forEachIndexed { index, line ->
            if (index > 0) height += LINE_GAP
            height += layoutFor(line, width).height
        }
        if (isHeader) return height + ACCENT_GAP + ACCENT_HEIGHT
        return maxOf(height, if (hasImage) IMAGE_SIZE else 0f) + CARD_PADDING * 2
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
                    color = SECONDARY_COLOR
                    typeface = Typeface.DEFAULT
                }
                WishPdfLineKind.BULLET -> {
                    textSize = NAME_TEXT_SIZE
                    color = INK_COLOR
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                WishPdfLineKind.DESCRIPTION -> {
                    textSize = BODY_TEXT_SIZE
                    color = SECONDARY_COLOR
                    typeface = Typeface.DEFAULT
                }
                WishPdfLineKind.PRICE -> {
                    textSize = BODY_TEXT_SIZE
                    color = ACCENT_INK
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                WishPdfLineKind.LINK -> {
                    textSize = BODY_TEXT_SIZE
                    color = ACCENT_INK
                    typeface = Typeface.DEFAULT
                }
            }
        }
}
