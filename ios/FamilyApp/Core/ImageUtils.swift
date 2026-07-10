// Image compression with orientation normalization: portrait camera uploads must come out
// upright, so the image is redrawn to .up orientation, downscaled to max 1024pt and encoded
// as JPEG (quality 0.85).
import UIKit

enum ImageUtils {
    static let maxDimension: CGFloat = 1024
    static let jpegQuality: CGFloat = 0.85

    /// Returns upright, downscaled JPEG data; nil when the input isn't an image.
    static func compressWithOrientation(
        _ data: Data,
        maxDimension: CGFloat = ImageUtils.maxDimension,
        quality: CGFloat = ImageUtils.jpegQuality
    ) -> Data? {
        guard let image = UIImage(data: data) else { return nil }
        return compressWithOrientation(image, maxDimension: maxDimension, quality: quality)
    }

    static func compressWithOrientation(
        _ image: UIImage,
        maxDimension: CGFloat = ImageUtils.maxDimension,
        quality: CGFloat = ImageUtils.jpegQuality
    ) -> Data? {
        let size = image.size
        let scale = min(1, maxDimension / max(size.width, size.height))
        let target = CGSize(width: size.width * scale, height: size.height * scale)

        // Drawing honours imageOrientation, so the output pixels are always .up.
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: target, format: format)
        let normalized = renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: target))
        }
        return normalized.jpegData(compressionQuality: quality)
    }
}
