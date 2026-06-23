-- Migration: Add conversation_participants table for explicit membership
-- Run this in the Supabase SQL Editor: https://supabase.com/dashboard → your project → SQL Editor
-- Safe to re-run: uses IF NOT EXISTS, CREATE OR REPLACE, DROP IF EXISTS before CREATE.

-- ────────────────────────────────────────────────────────────
-- 1. Junction table
-- ────────────────────────────────────────────────────────────
create table if not exists public.conversation_participants (
    id                uuid primary key default gen_random_uuid(),
    conversation_id   uuid not null references public.conversations(id) on delete cascade,
    user_id           uuid not null references public.users(id) on delete cascade,
    joined_at         timestamptz not null default now(),
    constraint uq_conversation_participant unique (conversation_id, user_id)
);

create index if not exists idx_conv_participants_conv on public.conversation_participants(conversation_id);
create index if not exists idx_conv_participants_user on public.conversation_participants(user_id);

-- ────────────────────────────────────────────────────────────
-- 2. Realtime
-- ────────────────────────────────────────────────────────────
alter publication supabase_realtime add table public.conversation_participants;
alter table public.conversation_participants replica identity full;

-- ────────────────────────────────────────────────────────────
-- 3. SECURITY DEFINER helper — prevents RLS recursion
--    Reads conversation_participants with RLS bypassed, so
--    conversations/messages policies can call it safely.
-- ────────────────────────────────────────────────────────────
create or replace function public.is_conversation_member(conv_id uuid, uid uuid)
returns boolean
language sql
security definer
stable
as $$
  select exists (
    select 1 from public.conversation_participants
    where conversation_id = conv_id and user_id = uid
  );
$$;

-- ────────────────────────────────────────────────────────────
-- 4. Backfill existing conversations into participants table
-- ────────────────────────────────────────────────────────────
insert into public.conversation_participants (conversation_id, user_id)
select id, user_from from public.conversations
on conflict (conversation_id, user_id) do nothing;

insert into public.conversation_participants (conversation_id, user_id)
select id, user_to from public.conversations where user_to is not null
on conflict (conversation_id, user_id) do nothing;

-- ────────────────────────────────────────────────────────────
-- 5. RLS on conversation_participants
-- ────────────────────────────────────────────────────────────
alter table public.conversation_participants enable row level security;

drop policy if exists "conv_participants_select"  on public.conversation_participants;
drop policy if exists "conv_participants_insert"  on public.conversation_participants;
drop policy if exists "conv_participants_delete"  on public.conversation_participants;

-- SELECT: you can see participants of conversations you belong to
create policy "conv_participants_select" on public.conversation_participants
    for select using (
        public.is_conversation_member(
            conversation_id,
            (select id from public.users where auth_id = auth.uid() limit 1)
        )
    );

-- INSERT: you can add participants if you created the conversation (user_from)
--         or are already a member (for adding to existing groups)
create policy "conv_participants_insert" on public.conversation_participants
    for insert with check (
        conversation_id in (
            select id from public.conversations
            where user_from = (select id from public.users where auth_id = auth.uid() limit 1)
        )
        or public.is_conversation_member(
            conversation_id,
            (select id from public.users where auth_id = auth.uid() limit 1)
        )
    );

-- DELETE: you can remove yourself, or remove others if you're a member
create policy "conv_participants_delete" on public.conversation_participants
    for delete using (
        user_id = (select id from public.users where auth_id = auth.uid() limit 1)
        or public.is_conversation_member(
            conversation_id,
            (select id from public.users where auth_id = auth.uid() limit 1)
        )
    );

-- ────────────────────────────────────────────────────────────
-- 6. Update conversations RLS — member check replaces family_id check
-- ────────────────────────────────────────────────────────────
drop policy if exists "conversations_access" on public.conversations;

create policy "conversations_access" on public.conversations
    for all using (
        -- creator fallback (handles the instant between INSERT and first participant row)
        user_from = (select id from public.users where auth_id = auth.uid() limit 1)
        or public.is_conversation_member(
            id,
            (select id from public.users where auth_id = auth.uid() limit 1)
        )
    );

-- ────────────────────────────────────────────────────────────
-- 7. Update messages RLS — scoped to participant membership
-- ────────────────────────────────────────────────────────────
drop policy if exists "messages_access" on public.messages;

create policy "messages_access" on public.messages
    for all using (
        public.is_conversation_member(
            conversation_id,
            (select id from public.users where auth_id = auth.uid() limit 1)
        )
    );
