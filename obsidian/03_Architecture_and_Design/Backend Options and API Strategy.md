# Backend Options and API Strategy

> **DECIDED — Supabase (Option C).** This note is retained as the decision record. The project
> shipped on **Supabase** (Postgres + Auth + Realtime + Storage); Room and all local persistence
> were removed. Both the Android and iOS apps use it as the sole backend. Details:
> [[Architecture and Design]] and [[../05_Implementation_Plan/Delivered/Backend & Data Sync]].

## Goal
Choose a backend/storage approach that supports:
- Family data sharing across users/devices
- Offline-first behavior where practical
- Chat/conversation synchronization
- Maintainable architecture during ongoing frontend migration

## Option A: Local-first only (SQLite/Room, no backend)
### Pros
- Fastest implementation
- Works fully offline
- Minimal infrastructure cost

### Cons
- No cross-device sync
- No shared real-time family state
- Limited long-term scalability for multi-user collaboration

## Option B: Firebase backend (Auth + Firestore + Storage)
### Pros
- Fast implementation for auth, realtime sync, and cloud storage
- Good support for chat-like data patterns
- Handles multi-device sync and conflict scenarios better than local-only

### Cons
- Vendor lock-in concerns
- Data modeling/query limits compared to full relational SQL backend
- Cost can grow with usage

## Option C: Supabase backend (Auth + Postgres + Realtime + Storage)
### Pros
- SQL data model with strong relational querying
- Built-in auth, storage, and realtime channels
- Better portability than tightly coupled proprietary stacks

### Cons
- Slightly more backend design effort than Firebase defaults
- Realtime and sync design still requires careful modeling

## Option D: Custom REST/GraphQL backend (e.g., Kotlin/Ktor, Node, or PHP)
### Pros
- Maximum control over domain logic and API behavior
- Can model exactly for product needs
- Full freedom in database and deployment architecture

### Cons
- Highest engineering and maintenance cost
- Requires security, auth, scaling, and ops ownership
- Slower to initial delivery

## Recommended direction (current)
Hybrid approach:
1. Keep local storage (Room) for offline caching and local responsiveness.
2. Add a cloud backend for shared family state and chat synchronization.
3. Prefer Supabase or Firebase for first production-grade backend iteration.
4. Introduce a clean repository/data-layer boundary so backend provider can be swapped later.

## Current project alignment notes
- Build/tooling baseline is now stable and should be preserved before backend expansion.
- AGP/Gradle/JDK verification is the first planning step in each delivery cycle.
- (Historical) This section pre-dates the decision; the app is now fully Compose (Android) + SwiftUI (iOS) on Supabase, with no legacy UI layer remaining.
- Naming normalization to English is largely completed, reducing domain-language ambiguity for API contracts.

## API direction
- Start with a clear domain API contract (family, users, lists, plans, chat, calendar).
- Use REST for initial simplicity, or provider-native SDK abstractions behind repositories.
- Add realtime channels only for modules that need it first (chat, shared lists, family status).

## Decision criteria
- Time to production
- Offline behavior quality
- Realtime requirements
- Security and auth requirements
- Cost and operational complexity
- Vendor lock-in tolerance
