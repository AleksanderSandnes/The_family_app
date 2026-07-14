// Renders a wishlist to a shareable A4 PDF for family members who don't use the app.
// Owner-facing export: it deliberately shows each wish's name, price, link and image —
// never reservation/claim status — so the surprise is preserved for the person exporting
// their own list. Pure UIKit (UIGraphicsPDFRenderer), unit-tested via WishlistPDFTests.
// Mirrors Android `WishlistPdf`. Images are fetched best-effort with a per-image timeout;
// a failed image never fails the export.
import UIKit

/// Formats a wish's free-text price for the export: bare numbers become "299 kr" (NOK);
/// anything else renders exactly as the user typed it. Pure — unit-tested.
func formatWishPrice(_ raw: String) -> String {
    let trimmed = raw.trimmingCharacters(in: .whitespaces)
    let digitsOnly = trimmed.replacingOccurrences(of: " ", with: "")
    let isBareNumber = digitsOnly.range(of: #"^\d+([.,]\d{1,2})?$"#, options: .regularExpression) != nil
    return isBareNumber ? "\(trimmed) kr" : trimmed
}

/// Compact display form of a wish link: scheme + www stripped, ellipsized. Pure — unit-tested.
func shortenedLink(_ url: String, maxLength: Int = 60) -> String {
    var stripped = url.trimmingCharacters(in: .whitespaces)
    for prefix in ["https://", "http://"] where stripped.hasPrefix(prefix) {
        stripped.removeFirst(prefix.count)
    }
    if stripped.hasPrefix("www.") { stripped.removeFirst(4) }
    if stripped.hasSuffix("/") { stripped.removeLast() }
    guard stripped.count > maxLength else { return stripped }
    return String(stripped.prefix(maxLength - 1)) + "…"
}

enum WishlistPDF {
    private static let pageSize = CGSize(width: 595.2, height: 841.8) // A4 at 72 dpi
    private static let margin: CGFloat = 48
    // Fixed dark inks — the page is always white, so we must NOT use dynamic colors like
    // .label/.secondaryLabel: in the app's dark mode those resolve near-white and vanish.
    private static let inkColor = UIColor.black
    private static let metaColor = UIColor(white: 0.32, alpha: 1)
    // Heirloom Indigo accent rule under the header (fixed, not a dynamic theme color).
    private static let accentColor = UIColor(red: 0x54 / 255.0, green: 0x57 / 255.0, blue: 0xE8 / 255.0, alpha: 1)

    private static let imageSize: CGFloat = 90
    private static let imageTextGap: CGFloat = 14
    private static let imageFetchTimeout: TimeInterval = 10
    private static let blockGap: CGFloat = 16

    /// Downloads wish images (best-effort) and writes the wishlist to a temp-directory PDF.
    /// Returns its file URL, or nil on failure. `subtitle` (e.g. "By Alice") is passed in
    /// already localized so this stays free of the main-actor `L` helper.
    static func make(name: String, subtitle: String, wishes: [WishModel]) async -> URL? {
        let images = await fetchImages(for: wishes)
        return render(name: name, subtitle: subtitle, wishes: wishes, images: images)
    }

    /// Fetches each wish's image; failures and timeouts are skipped so the export always succeeds.
    private static func fetchImages(for wishes: [WishModel]) async -> [String: UIImage] {
        var result: [String: UIImage] = [:]
        for wish in wishes {
            guard let raw = wish.imageUrl?.trimmingCharacters(in: .whitespaces), !raw.isEmpty,
                  let url = URL(string: raw)
            else { continue }
            let request = URLRequest(url: url, timeoutInterval: imageFetchTimeout)
            if let (data, _) = try? await URLSession.shared.data(for: request),
               let image = UIImage(data: data) {
                result[wish.id] = image
            }
        }
        return result
    }

    /// Synchronous rendering shell around the fetched images.
    private static func render(
        name: String, subtitle: String, wishes: [WishModel], images: [String: UIImage]
    ) -> URL? {
        let pageRect = CGRect(origin: .zero, size: pageSize)
        let contentWidth = pageSize.width - margin * 2
        let renderer = UIGraphicsPDFRenderer(bounds: pageRect)
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(fileName(for: name))

        do {
            try renderer.writePDF(to: url) { ctx in
                ctx.beginPage()
                var cursorY = margin

                cursorY += draw(
                    name, at: CGPoint(x: margin, y: cursorY), width: contentWidth,
                    font: .systemFont(ofSize: 26, weight: .bold), color: Self.inkColor
                )
                if !subtitle.isEmpty {
                    cursorY += 6
                    cursorY += draw(
                        subtitle, at: CGPoint(x: margin, y: cursorY), width: contentWidth,
                        font: .systemFont(ofSize: 13, weight: .regular), color: Self.metaColor
                    )
                }
                // Short indigo accent rule under the header.
                cursorY += 10
                let accent = UIBezierPath(
                    roundedRect: CGRect(x: margin, y: cursorY, width: 64, height: 3), cornerRadius: 1.5
                )
                accentColor.setFill()
                accent.fill()
                cursorY += 3 + 24

                let bodyFont = UIFont.systemFont(ofSize: 15, weight: .semibold)
                let metaFont = UIFont.systemFont(ofSize: 12, weight: .regular)
                for wish in wishes where !wish.text.trimmingCharacters(in: .whitespaces).isEmpty {
                    let image = images[wish.id]
                    let needed: CGFloat = image != nil ? imageSize + blockGap : 40
                    cursorY = startPageIfNeeded(cursorY, ctx: ctx, needed: needed)

                    let blockTop = cursorY
                    let textX = margin + (image != nil ? imageSize + imageTextGap : 0)
                    let textWidth = contentWidth - (image != nil ? imageSize + imageTextGap : 0)

                    if let image {
                        drawSquare(image, at: CGPoint(x: margin, y: blockTop))
                    }

                    cursorY += draw(
                        wish.text, at: CGPoint(x: textX, y: cursorY), width: textWidth,
                        font: bodyFont, color: Self.inkColor
                    )
                    if let price = wish.price, !price.trimmingCharacters(in: .whitespaces).isEmpty {
                        cursorY += 3
                        cursorY += draw(
                            formatWishPrice(price), at: CGPoint(x: textX, y: cursorY), width: textWidth,
                            font: metaFont, color: Self.metaColor
                        )
                    }
                    if let link = wish.link, !link.trimmingCharacters(in: .whitespaces).isEmpty {
                        cursorY += 3
                        cursorY += draw(
                            shortenedLink(link), at: CGPoint(x: textX, y: cursorY), width: textWidth,
                            font: metaFont, color: Self.metaColor
                        )
                    }
                    // The block occupies at least the thumbnail's height.
                    if image != nil { cursorY = max(cursorY, blockTop + imageSize) }
                    cursorY += blockGap
                }
            }
            return url
        } catch {
            return nil
        }
    }

    /// Center-crops [image] into an `imageSize` square at [origin].
    private static func drawSquare(_ image: UIImage, at origin: CGPoint) {
        let rect = CGRect(x: origin.x, y: origin.y, width: imageSize, height: imageSize)
        guard let ctx = UIGraphicsGetCurrentContext() else { return }
        ctx.saveGState()
        UIBezierPath(rect: rect).addClip()
        let scale = max(imageSize / image.size.width, imageSize / image.size.height)
        let drawSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let drawOrigin = CGPoint(
            x: rect.midX - drawSize.width / 2,
            y: rect.midY - drawSize.height / 2
        )
        image.draw(in: CGRect(origin: drawOrigin, size: drawSize))
        ctx.restoreGState()
    }

    /// Draws wrapped text and returns the height it consumed.
    private static func draw(
        _ text: String, at point: CGPoint, width: CGFloat, font: UIFont, color: UIColor
    ) -> CGFloat {
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineBreakMode = .byWordWrapping
        let attrs: [NSAttributedString.Key: Any] = [
            .font: font, .foregroundColor: color, .paragraphStyle: paragraph,
        ]
        let bounds = CGSize(width: width, height: .greatestFiniteMagnitude)
        let rect = (text as NSString).boundingRect(
            with: bounds, options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: attrs, context: nil
        )
        (text as NSString).draw(
            with: CGRect(x: point.x, y: point.y, width: width, height: ceil(rect.height)),
            options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: attrs, context: nil
        )
        return ceil(rect.height)
    }

    /// Starts a new page (resetting the cursor to the top margin) when the next block
    /// wouldn't fit above the bottom margin.
    private static func startPageIfNeeded(
        _ cursorY: CGFloat, ctx: UIGraphicsPDFRendererContext, needed: CGFloat
    ) -> CGFloat {
        if cursorY + needed > pageSize.height - margin {
            ctx.beginPage()
            return margin
        }
        return cursorY
    }

    private static func fileName(for name: String) -> String {
        let base = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let safe = base.isEmpty ? "Wishlist" : base
        let cleaned = safe.components(separatedBy: CharacterSet(charactersIn: "/\\:?%*|\"<>"))
            .joined(separator: "-")
        return "\(cleaned).pdf"
    }
}
