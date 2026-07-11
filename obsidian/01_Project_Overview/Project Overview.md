# Project Overview

## Purpose
The Family App helps families coordinate everyday life in one shared space. It is now a
**two-platform product** — a native Android app and a native iOS app — sharing a single
Supabase backend (Postgres + Realtime + Storage + Auth).

## Platforms
- **`android/`** — Jetpack Compose + Material 3, single-Activity, package `com.sandnes.familyapp`.
- **`ios/`** — native SwiftUI (iOS 26, "Liquid Glass" design), XcodeGen + supabase-swift.
- **`supabase/`** — the shared backend (schema baseline, incremental migrations, edge functions).

Both apps target 1:1 feature and design parity. See [[03_Architecture_and_Design/Architecture and Design]].

## Core domains
- Shopping lists
- Meal planning
- Calendar (private/coloured events, attendees)
- Birthdays
- Wishlists (secret reservations, shareable links, PDF export)
- Family chat (text/image/voice, reactions, replies, receipts, typing)
- Family map / location sharing
- Family relations + member profiles

## Current state
- The full Android app is Kotlin + Jetpack Compose + Material 3 on Supabase (all legacy
  Java/Fragment/SQLite/Room code removed long ago).
- The native iOS app has shipped (merged to `master`) and now leads on the newest features + a
  Liquid Glass redesign + a new tab-bar IA.
- An **Android ⇄ iOS parity track** ([[05_Implementation_Plan/Android Parity Track]]) is bringing
  Android up to the iOS feature set and look.

## Delivery workflow
- Work is delivered through branch progression: `task -> test -> master`.
- Never commit directly to `master` or `test`.
