// Supabase Realtime subscription helper — the iOS twin of the Android pattern:
//   1. channel name "tablename-\(familyId)" (must be unique per subscription)
//   2. any postgres change event → call the FULL reload closure (never partial merge)
//   3. teardown removes the channel
import Foundation
import Supabase

@MainActor
final class RealtimeObserver {
    private var channel: RealtimeChannelV2?
    private var task: Task<Void, Never>?

    /// Subscribes to postgres changes on `table` and calls `onChange` for every event.
    /// `scope` is appended to the channel name for uniqueness (Android: the familyId).
    /// `filter` optionally narrows server-side, e.g. `"family_id=eq.\(familyId)"`.
    func start(
        table: String,
        scope: String,
        filter: String? = nil,
        onChange: @escaping @MainActor () async -> Void
    ) {
        stop()
        let client = SupabaseClientProvider.client
        let channel = client.channel("\(table)-\(scope)")
        self.channel = channel

        let changes = channel.postgresChange(
            AnyAction.self,
            schema: "public",
            table: table,
            filter: filter
        )
        task = Task {
            await channel.subscribe()
            for await _ in changes {
                guard !Task.isCancelled else { break }
                await onChange()
            }
        }
    }

    func stop() {
        task?.cancel()
        task = nil
        if let channel {
            let client = SupabaseClientProvider.client
            Task { await client.removeChannel(channel) }
        }
        channel = nil
    }

    isolated deinit {
        task?.cancel()
        if let channel {
            let client = SupabaseClientProvider.client
            Task { await client.removeChannel(channel) }
        }
    }
}
