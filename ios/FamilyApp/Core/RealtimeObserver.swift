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
        filter: RealtimePostgresFilter? = nil,
        onChange: @escaping @MainActor () async -> Void
    ) {
        // Tear the previous subscription down and *await* its removal before recreating the
        // topic. The client reuses a channel by topic and rejects postgres callbacks added
        // after `subscribe()`, so on a re-entrant start() (view re-appear, refresh) a plain
        // fire-and-forget removeChannel would let `client.channel(topic:)` hand back a still-
        // subscribed channel — `postgresChange` would then log "Cannot add postgres_changes
        // callbacks after subscribe()". Sequencing removal → create → register → subscribe in
        // one task guarantees a fresh, unsubscribed channel.
        let previousChannel = channel
        task?.cancel()
        channel = nil

        let client = SupabaseClientProvider.client
        let topic = "\(table)-\(scope)"

        task = Task { [weak self] in
            if let previousChannel {
                await client.removeChannel(previousChannel)
            }
            guard !Task.isCancelled else { return }

            let channel = client.channel(topic)
            self?.channel = channel

            // Register the postgres listener *before* subscribing (registration is synchronous).
            let changes = channel.postgresChange(
                AnyAction.self,
                schema: "public",
                table: table,
                filter: filter
            )

            do {
                try await channel.subscribeWithError()
            } catch {
                await client.removeChannel(channel)
                return
            }
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
