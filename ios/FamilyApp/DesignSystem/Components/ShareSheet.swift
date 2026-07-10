// Thin wrapper around UIActivityViewController for presenting the system share sheet with
// arbitrary items (a generated PDF file URL, a share link, …). Presented via `.sheet(item:)`
// using ShareItem so the share target is built on demand rather than at render time.
import SwiftUI
import UIKit

/// Identifiable box so a just-built share payload can drive `.sheet(item:)`.
struct ShareItem: Identifiable {
    let id = UUID()
    let items: [Any]
}

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context _: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_: UIActivityViewController, context _: Context) {}
}
