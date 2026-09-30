-- Migration: push notifications (FCM) for events, birthdays, and messages.

-- 1. Server-readable notification preference mirror
alter table public.users add column if not exists notifications_enabled boolean not null default true;
alter table public.users add column if not exists notify_days_before     int     not null default 1;

-- 2. Device push tokens
create table if not exists public.device_push_tokens (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references public.users(id) on delete cascade,
    token       text not null unique,
    platform    text not null default 'android',
    updated_at  timestamptz not null default now()
);

create index if not exists idx_device_push_tokens_user on public.device_push_tokens(user_id);

-- 3. RLS — a user manages only their own device tokens
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
grant select, insert, update, delete on public.device_push_tokens to authenticated;;
