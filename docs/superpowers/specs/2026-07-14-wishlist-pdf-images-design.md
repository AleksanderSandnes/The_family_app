# Wishlist PDF v2 — images, NOK prices, polished layout

**Date:** 2026-07-14 · **Status:** Approved · **Branch:** `feat/wishlist-pdf-images`

## Problem

The wishlist PDF export (share with people outside the app) is text-only. Wishes carry optional `price`, `link`, and `imageUrl` — the image never makes it into the PDF, prices render as raw text, and the layout is plain.

## Decisions

- **Images:** every wish with an `imageUrl` renders a rounded ~90 pt thumbnail, left of its text. Images are fetched via the app's image loader (Coil on Android, URLSession on iOS) with a 10 s timeout each; a failed/missing image renders the block without a thumbnail — the export never fails because of an image. `WishlistPdf.write` becomes suspend/async (call site already runs it off the main thread).
- **NOK prices:** pure helper `formatWishPrice` — a bare number ("299", "299,50") renders as "299 kr"; anything else exactly as the user typed.
- **Links:** pure helper `shortenedLink` — scheme/`www.` stripped, ellipsized at ~60 chars.
- **Layout:** header (title + subtitle) over a thin Heirloom-Indigo (#5457E8) accent rule; each wish is a block (thumbnail left; bold name, price, link right); blocks never split across pages (existing `paginateBlockHeights` reused with image-aware heights). Bullet-glyph prefix dropped — the block layout replaces it. Reservation status remains excluded.
- **Scope:** Android + iOS parity. The other half of the original request — editing shopping items in the detail view — **already exists on both platforms** (tap the item text → inline edit → `renameItem`); no work needed.

## Testing

Pure helpers stay unit-tested: updated `wishlistPdfBlocks` expectations (no bullet prefix, `imageUrl` carried on blocks, formatted price, shortened link), new `formatWishPrice` and `shortenedLink` cases; image-aware block heights in pagination remain pure. iOS mirrors in `FamilyAppTests`. Rendering shells (`PdfDocument` / `UIGraphicsPDFRenderer`) stay untested, as before. Manual check: export a wishlist with image+price+link wishes and one plain wish; verify thumbnails, "kr" prices, layout, and multi-page behavior.
