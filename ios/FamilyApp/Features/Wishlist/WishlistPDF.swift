// Renders a wishlist to a shareable A4 PDF for family members who don't use the app.
// Owner-facing export: it deliberately shows only each wish's name, price and link —
// never reservation/claim status — so the surprise is preserved for the person exporting
// their own list. Pure UIKit (UIGraphicsPDFRenderer), unit-tested via WishlistPDFTests.
import UIKit

enum WishlistPDF {
    private static let pageSize = CGSize(width: 595.2, height: 841.8) // A4 at 72 dpi
    private static let margin: CGFloat = 48

    /// Writes the wishlist to a temp-directory PDF and returns its file URL, or nil on failure.
    static func make(name: String, ownerName: String, wishes: [WishModel]) -> URL? {
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
                    font: .systemFont(ofSize: 26, weight: .bold), color: .label
                )
                if !ownerName.isEmpty {
                    cursorY += 6
                    cursorY += draw(
                        L("By \(ownerName)"), at: CGPoint(x: margin, y: cursorY), width: contentWidth,
                        font: .systemFont(ofSize: 13, weight: .regular), color: .secondaryLabel
                    )
                }
                cursorY += 24

                let bodyFont = UIFont.systemFont(ofSize: 15, weight: .semibold)
                let metaFont = UIFont.systemFont(ofSize: 12, weight: .regular)
                for wish in wishes where !wish.text.trimmingCharacters(in: .whitespaces).isEmpty {
                    cursorY = startPageIfNeeded(cursorY, ctx: ctx, needed: 40)
                    cursorY += draw(
                        "•  \(wish.text)", at: CGPoint(x: margin, y: cursorY), width: contentWidth,
                        font: bodyFont, color: .label
                    )
                    if let price = wish.price, !price.trimmingCharacters(in: .whitespaces).isEmpty {
                        cursorY += draw(
                            price, at: CGPoint(x: margin + 18, y: cursorY), width: contentWidth - 18,
                            font: metaFont, color: .secondaryLabel
                        )
                    }
                    if let link = wish.link, !link.trimmingCharacters(in: .whitespaces).isEmpty {
                        cursorY += draw(
                            link, at: CGPoint(x: margin + 18, y: cursorY), width: contentWidth - 18,
                            font: metaFont, color: .secondaryLabel
                        )
                    }
                    cursorY += 12
                }
            }
            return url
        } catch {
            return nil
        }
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
