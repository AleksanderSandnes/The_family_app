// Type scale — mirrors Type.kt (SF Pro replaces the Android sans-serif; same sizes
// and weights: 34/28/23/19/17/15/16/14/12).
import SwiftUI

extension Font {
    static let displaySmall = Font.system(size: 34, weight: .bold)
    static let headlineLarge = Font.system(size: 28, weight: .bold)
    static let headlineMedium = Font.system(size: 23, weight: .bold)
    static let headlineSmall = Font.system(size: 19, weight: .semibold)
    static let titleLarge = Font.system(size: 17, weight: .semibold)
    static let titleMedium = Font.system(size: 15, weight: .semibold)
    static let bodyLarge = Font.system(size: 16, weight: .regular)
    static let bodyMedium = Font.system(size: 14, weight: .regular)
    static let labelLarge = Font.system(size: 14, weight: .semibold)
    static let labelMedium = Font.system(size: 12, weight: .medium)
}
