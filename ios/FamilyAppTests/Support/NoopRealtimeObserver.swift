// A RealtimeObserving that does nothing — injected into ViewModels under test so they
// never open a live Supabase channel.
@testable import FamilyApp
import Supabase

@MainActor
final class NoopRealtimeObserver: RealtimeObserving {
    private(set) var startCount = 0
    func start(
        table _: String,
        scope _: String,
        filter _: RealtimePostgresFilter?,
        onChange _: @escaping @MainActor () async -> Void
    ) { startCount += 1 }
    func stop() {}
}
