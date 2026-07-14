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
    private static let margin: CGFloat = 44

    // Fixed Glass House light-mode inks — never dynamic colors like .label/.secondaryLabel:
    // in the app's dark mode those resolve near-white and vanish on the light page.
    private static let canvasColor = UIColor(red: 0xEF / 255.0, green: 0xF1 / 255.0, blue: 0xF8 / 255.0, alpha: 1)
    private static let cardBorderColor = UIColor(red: 0x16 / 255.0, green: 0x19 / 255.0, blue: 0x2A / 255.0, alpha: 0.10)
    private static let inkColor = UIColor(red: 0x16 / 255.0, green: 0x19 / 255.0, blue: 0x2A / 255.0, alpha: 1)
    private static let secondaryColor = UIColor(red: 0x5F / 255.0, green: 0x67 / 255.0, blue: 0x80 / 255.0, alpha: 1)
    private static let accentInk = UIColor(red: 0x4F / 255.0, green: 0x55 / 255.0, blue: 0xE6 / 255.0, alpha: 1)
    private static let captionColor = UIColor(red: 0x8B / 255.0, green: 0x92 / 255.0, blue: 0xAC / 255.0, alpha: 1)
    // The one sanctioned brand gradient (Heirloom Indigo → violet) for the header rule.
    private static let accentStart = UIColor(red: 0x54 / 255.0, green: 0x57 / 255.0, blue: 0xE8 / 255.0, alpha: 1)
    private static let accentEnd = UIColor(red: 0x7C / 255.0, green: 0x3A / 255.0, blue: 0xED / 255.0, alpha: 1)

    private static let cardPadding: CGFloat = 12
    private static let cardCornerRadius: CGFloat = 14
    private static let imageSize: CGFloat = 84
    private static let imageTextGap: CGFloat = 14
    private static let imageFetchTimeout: TimeInterval = 10
    private static let blockGap: CGFloat = 12
    private static let footerText = "The Family App  ·  thefamilyapp.app"

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
                beginStyledPage(ctx)
                var cursorY = margin

                cursorY += draw(
                    name, at: CGPoint(x: margin, y: cursorY), width: contentWidth,
                    font: .systemFont(ofSize: 26, weight: .bold), color: Self.inkColor
                )
                if !subtitle.isEmpty {
                    cursorY += 6
                    cursorY += draw(
                        subtitle, at: CGPoint(x: margin, y: cursorY), width: contentWidth,
                        font: .systemFont(ofSize: 13, weight: .regular), color: Self.secondaryColor
                    )
                }
                // Brand-gradient accent rule under the header.
                cursorY += 10
                drawAccentRule(at: CGPoint(x: margin, y: cursorY))
                cursorY += 3 + 20

                for wish in wishes where !wish.text.trimmingCharacters(in: .whitespaces).isEmpty {
                    let image = images[wish.id]
                    let cardHeight = measureCard(wish: wish, image: image, contentWidth: contentWidth)
                    if cursorY + cardHeight > pageSize.height - margin - 24 {
                        beginStyledPage(ctx)
                        cursorY = margin
                    }
                    drawWishCard(
                        wish: wish, image: image,
                        at: CGPoint(x: margin, y: cursorY),
                        contentWidth: contentWidth, cardHeight: cardHeight
                    )
                    cursorY += cardHeight + blockGap
                }
            }
            return url
        } catch {
            return nil
        }
    }

    /// Starts a page pre-filled with the ambient canvas and the footer caption.
    private static func beginStyledPage(_ ctx: UIGraphicsPDFRendererContext) {
        ctx.beginPage()
        canvasColor.setFill()
        UIBezierPath(rect: CGRect(origin: .zero, size: pageSize)).fill()
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        (footerText as NSString).draw(
            in: CGRect(x: 0, y: pageSize.height - margin / 2 - 9, width: pageSize.width, height: 14),
            withAttributes: [
                .font: UIFont.systemFont(ofSize: 9, weight: .regular),
                .foregroundColor: captionColor,
                .paragraphStyle: paragraph,
            ]
        )
    }

    /// Short Heirloom-Indigo → violet gradient rule.
    private static func drawAccentRule(at origin: CGPoint) {
        guard let ctx = UIGraphicsGetCurrentContext() else { return }
        ctx.saveGState()
        let rect = CGRect(x: origin.x, y: origin.y, width: 72, height: 3)
        UIBezierPath(roundedRect: rect, cornerRadius: 1.5).addClip()
        let colors = [accentStart.cgColor, accentEnd.cgColor] as CFArray
        if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: nil) {
            ctx.drawLinearGradient(
                gradient,
                start: CGPoint(x: rect.minX, y: rect.midY),
                end: CGPoint(x: rect.maxX, y: rect.midY),
                options: []
            )
        }
        ctx.restoreGState()
    }

    private static let nameFont = UIFont.systemFont(ofSize: 15, weight: .bold)
    private static let bodyFont = UIFont.systemFont(ofSize: 12, weight: .regular)
    private static let priceFont = UIFont.systemFont(ofSize: 12, weight: .bold)

    /// The wish's text lines: (string, font, color), in draw order.
    private static func cardLines(for wish: WishModel) -> [(String, UIFont, UIColor)] {
        var lines: [(String, UIFont, UIColor)] = [(wish.text, nameFont, inkColor)]
        if let description = wish.description?.trimmingCharacters(in: .whitespaces), !description.isEmpty {
            lines.append((description, bodyFont, secondaryColor))
        }
        if let price = wish.price?.trimmingCharacters(in: .whitespaces), !price.isEmpty {
            lines.append((formatWishPrice(price), priceFont, accentInk))
        }
        if let link = wish.link?.trimmingCharacters(in: .whitespaces), !link.isEmpty {
            lines.append((shortenedLink(link), bodyFont, accentInk))
        }
        return lines
    }

    /// Total card height including padding; text column accounts for the thumbnail.
    private static func measureCard(wish: WishModel, image: UIImage?, contentWidth: CGFloat) -> CGFloat {
        let textWidth = contentWidth - cardPadding * 2 - (image != nil ? imageSize + imageTextGap : 0)
        var textHeight: CGFloat = 0
        for (index, line) in cardLines(for: wish).enumerated() {
            if index > 0 { textHeight += 4 }
            textHeight += measure(line.0, width: textWidth, font: line.1)
        }
        let inner = max(textHeight, image != nil ? imageSize : 0)
        return inner + cardPadding * 2
    }

    /// One wish as a white rounded card with hairline border, image left, text right.
    private static func drawWishCard(
        wish: WishModel, image: UIImage?, at origin: CGPoint, contentWidth: CGFloat, cardHeight: CGFloat
    ) {
        let card = CGRect(x: origin.x, y: origin.y, width: contentWidth, height: cardHeight)
        let path = UIBezierPath(roundedRect: card, cornerRadius: cardCornerRadius)
        UIColor.white.setFill()
        path.fill()
        cardBorderColor.setStroke()
        path.lineWidth = 1
        path.stroke()

        let innerLeft = origin.x + cardPadding
        let innerTop = origin.y + cardPadding
        if let image {
            drawSquare(image, at: CGPoint(x: innerLeft, y: innerTop))
        }
        let textX = innerLeft + (image != nil ? imageSize + imageTextGap : 0)
        let textWidth = card.maxX - cardPadding - textX

        var cursorY = innerTop
        for (index, line) in cardLines(for: wish).enumerated() {
            if index > 0 { cursorY += 4 }
            cursorY += draw(line.0, at: CGPoint(x: textX, y: cursorY), width: textWidth, font: line.1, color: line.2)
        }
    }

    /// Measures wrapped text height without drawing.
    private static func measure(_ text: String, width: CGFloat, font: UIFont) -> CGFloat {
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineBreakMode = .byWordWrapping
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .paragraphStyle: paragraph]
        let bounds = CGSize(width: width, height: .greatestFiniteMagnitude)
        let rect = (text as NSString).boundingRect(
            with: bounds, options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: attrs, context: nil
        )
        return ceil(rect.height)
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

    private static func fileName(for name: String) -> String {
        let base = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let safe = base.isEmpty ? "Wishlist" : base
        let cleaned = safe.components(separatedBy: CharacterSet(charactersIn: "/\\:?%*|\"<>"))
            .joined(separator: "-")
        return "\(cleaned).pdf"
    }
}
