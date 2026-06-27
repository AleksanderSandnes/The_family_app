-- Migration: push notifications (FCM) for events, birthdays, and messages.
-- Run this in the Supabase SQL Editor: https://supabase.com/dashboard → SQL Editor.
-- Safe to re-run: uses IF NOT EXISTS / DROP POLICY IF EXISTS before CREATE.
--
-- Adds:
--   1. device_push_tokens — one row per device FCM token, owned by a user.
--   2. users.notifications_enabled / users.notify_days_before — server-readable mirror
--      of the client DataStore settings, so the daily-reminders Edge Function can honour
--      each user's preference (the app keeps DataStore as its UI source of truth and
--      syncs these columns via FamilyRepository.updateUserNotificationPrefs).
--
-- Delivery is driven by two Edge Functions (supabase/functions/):
--   - push-on-message   ← Database Webhook on `messages` INSERT (instant chat push)
--   - daily-reminders   ← pg_cron schedule (birthday/event reminders each morning)

-- ────────────────────────────────────────────────────────────
-- 1. Server-readable notification preference mirror
-- ────────────────────────────────────────────────────────────
alter table public.users add column if not exists notifications_enabled boolean not null default true;
alter table public.users add column if not exists notify_days_before     int     not null default 1;

-- ────────────────────────────────────────────────────────────
-- 2. Device push tokens
-- ────────────────────────────────────────────────────────────
create table if not exists public.device_push_tokens (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references public.users(id) on delete cascade,
    token       text not null unique,
    platform    text not null default 'android',
    updated_at  timestamptz not null default now()
);

create index if not exists idx_device_push_tokens_user on public.device_push_tokens(user_id);

-- ────────────────────────────────────────────────────────────
-- 3. RLS — a user manages only their own device tokens
--    (resolves the app user id from auth.uid(), per project RLS pattern; the
--     `limit 1` is required to keep the subquery single-row).
-- ────────────────────────────────────────────────────────────
alter table public.device_push_tokens enable row level security;

drop policy if exists "device_push_tokens_select" on public.device_push_tokens;
drop policy if exists "device_push_tokens_insert" on public.device_push_tokens;
drop policy if exists "device_push_tokens_update" on public.device_push_tokens;
drop policy if exists "device_push_tokens_delete" on public.device_push_tokens;

create policy "device_push_tokens_select" on public.device_push_tokens
    for select using (
        user_id = (select id from public.users where auth_id = auth.uid() limit 1)
    );

create policy "device_push_tokens_insert" on public.device_push_tokens
    for insert with check (
        user_id = (select id from public.users where auth_id = auth.uid() limit 1)
    );

-- UPDATE covers the upsert path (ON CONFLICT (token) → updates user_id/updated_at when a
-- device's token is re-registered, e.g. after the same person logs in on that device).
create policy "device_push_tokens_update" on public.device_push_tokens
    for update using (
        user_id = (select id from public.users where auth_id = auth.uid() limit 1)
    ) with check (
        user_id = (select id from public.users where auth_id = auth.uid() limit 1)
    );

create policy "device_push_tokens_delete" on public.device_push_tokens
    for delete using (
        user_id = (select id from public.users where auth_id = auth.uid() limit 1)
    );

-- Table-level privileges the RLS policies are written against.
grant select, insert, update, delete on public.device_push_tokens to authenticated;

-- NOTE: the Edge Functions read tokens / participants / preferences with the
-- service_role key, which bypasses RLS — no extra read policy is needed for them.
