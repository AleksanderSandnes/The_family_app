// QR rendering via CoreImage — the iOS twin of QrCode.kt (ZXing on Android).
import CoreImage.CIFilterBuiltins
import UIKit

/// Renders `content` as a QR code image. Returns nil if encoding fails.
func generateQrImage(content: String, size: CGFloat = 640) -> UIImage? {
    let filter = CIFilter.qrCodeGenerator()
    filter.message = Data(content.utf8)
    guard let output = filter.outputImage else { return nil }
    let scale = size / output.extent.width
    let scaled = output.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
    guard let cgImage = CIContext().createCGImage(scaled, from: scaled.extent) else { return nil }
    return UIImage(cgImage: cgImage)
}
