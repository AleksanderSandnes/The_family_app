// Nuke image pipeline. AsyncImage lacks the disk-cache control chat images and avatars
// need, so all remote images go through Nuke's LazyImage.
import Foundation
import Nuke

enum ImagePipelineConfig {
    /// Call once at app launch.
    static func configure() {
        var configuration = ImagePipeline.Configuration.withDataCache(
            name: "com.sandnes.familyapp.images",
            sizeLimit: 256 * 1024 * 1024
        )
        configuration.isProgressiveDecodingEnabled = true
        ImagePipeline.shared = ImagePipeline(configuration: configuration)
    }
}
