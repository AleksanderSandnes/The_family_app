// Voice notes — the iOS twin of the Android MediaRecorder flow + VoiceNoteMessage:
// AVAudioRecorder producing m4a/AAC into the same chat-media bucket, AVAudioPlayer
// playback with progress.
import AVFoundation
import Observation
import SwiftUI

/// Records m4a/AAC voice notes — parity with Android's MediaRecorder settings.
@Observable
@MainActor
final class VoiceRecorder {
    private(set) var isRecording = false
    private(set) var seconds = 0
    /// Rolling normalised (0…1) mic levels, newest last — drives the live waveform.
    private(set) var levels: [CGFloat] = Array(repeating: 0.06, count: waveformBarCount)

    static let waveformBarCount = 34

    private var recorder: AVAudioRecorder?
    private var fileURL: URL?
    private var tickTask: Task<Void, Never>?

    func start() async -> Bool {
        let session = AVAudioSession.sharedInstance()
        let granted = await AVAudioApplication.requestRecordPermission()
        guard granted else { return false }
        do {
            try session.setCategory(.playAndRecord, mode: .default, options: .defaultToSpeaker)
            try session.setActive(true)
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("voice_\(Int(Date().timeIntervalSince1970 * 1000)).m4a")
            fileURL = url
            let settings: [String: Any] = [
                AVFormatIDKey: kAudioFormatMPEG4AAC,
                AVSampleRateKey: 44100,
                AVNumberOfChannelsKey: 1,
                AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue,
            ]
            let recorder = try AVAudioRecorder(url: url, settings: settings)
            recorder.isMeteringEnabled = true
            recorder.record()
            self.recorder = recorder
            isRecording = true
            seconds = 0
            levels = Array(repeating: 0.06, count: Self.waveformBarCount)
            tickTask = Task { [weak self] in
                var elapsedMs = 0
                while !Task.isCancelled {
                    try? await Task.sleep(for: .milliseconds(60))
                    guard let self, isRecording, let recorder = self.recorder else { break }
                    recorder.updateMeters()
                    // Average power is dB in −160…0; map a useful −55…0 window to 0…1.
                    let power = recorder.averagePower(forChannel: 0)
                    let norm = max(0.06, min(1, CGFloat((power + 55) / 55)))
                    levels = Array(levels.dropFirst()) + [norm]
                    elapsedMs += 60
                    seconds = elapsedMs / 1000
                }
            }
            return true
        } catch {
            return false
        }
    }

    /// Stops recording; returns the audio data and filename when `send` is true.
    func stop(send: Bool) -> (data: Data, filename: String)? {
        isRecording = false
        tickTask?.cancel()
        recorder?.stop()
        recorder = nil
        defer {
            if let fileURL { try? FileManager.default.removeItem(at: fileURL) }
            fileURL = nil
        }
        guard send, let fileURL, let data = try? Data(contentsOf: fileURL) else { return nil }
        return (data, fileURL.lastPathComponent)
    }
}

/// Live mic waveform shown in the recording bar — bars react to input level.
struct LiveWaveform: View {
    let levels: [CGFloat]
    let tint: Color

    var body: some View {
        GeometryReader { geo in
            let count = max(levels.count, 1)
            let spacing: CGFloat = 3
            let barWidth = max(2, (geo.size.width - spacing * CGFloat(count - 1)) / CGFloat(count))
            HStack(alignment: .center, spacing: spacing) {
                ForEach(Array(levels.enumerated()), id: \.offset) { _, level in
                    Capsule()
                        .fill(tint.opacity(0.35 + level * 0.65))
                        .frame(width: barWidth, height: max(3, level * geo.size.height))
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
            .animation(.easeOut(duration: 0.08), value: levels)
        }
        .frame(height: 30)
    }
}

/// Pulsing red dot indicating an active recording.
struct RecordingPulse: View {
    @State private var on = false
    var body: some View {
        Circle()
            .fill(Color.appError)
            .frame(width: 10, height: 10)
            .opacity(on ? 0.35 : 1)
            .animation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true), value: on)
            .onAppear { on = true }
    }
}

/// Playable voice-note bubble content.
struct VoiceNoteView: View {
    let url: String
    let tint: Color

    @State private var player = VoicePlayer()

    var body: some View {
        HStack(spacing: Spacing.sm) {
            Button {
                player.toggle(urlString: url)
            } label: {
                Image(systemName: player.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                    .font(.system(size: 30))
                    .foregroundStyle(tint)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(player.isPlaying ? L("Pause voice message") : L("Play voice message"))
            // Simple waveform placeholder (Android parity).
            HStack(spacing: 2) {
                ForEach(0..<18, id: \.self) { index in
                    Capsule()
                        .fill(tint.opacity(index.isMultiple(of: 3) ? 0.9 : 0.5))
                        .frame(width: 2.5, height: CGFloat([8, 14, 10, 18, 12, 16][index % 6]))
                }
            }
            Text(player.durationLabel)
                .font(.labelMedium)
                .foregroundStyle(tint)
        }
        .padding(.vertical, 4)
    }
}

@Observable
@MainActor
final class VoicePlayer: NSObject, AVAudioPlayerDelegate {
    private(set) var isPlaying = false
    private(set) var durationLabel = "0:00"

    private var player: AVAudioPlayer?
    private var loadTask: Task<Void, Never>?

    func toggle(urlString: String) {
        if isPlaying {
            player?.pause()
            isPlaying = false
            return
        }
        if let player {
            player.play()
            isPlaying = true
            return
        }
        loadTask?.cancel()
        loadTask = Task {
            guard let url = URL(string: urlString),
                  let (data, _) = try? await URLSession.shared.data(from: url),
                  let player = try? AVAudioPlayer(data: data)
            else { return }
            try? AVAudioSession.sharedInstance().setCategory(.playback)
            player.delegate = self
            self.player = player
            durationLabel = Self.format(player.duration)
            player.play()
            isPlaying = true
        }
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in
            isPlaying = false
        }
    }

    private static func format(_ duration: TimeInterval) -> String {
        let total = Int(duration.rounded())
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}
