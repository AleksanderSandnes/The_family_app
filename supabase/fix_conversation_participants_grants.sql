-- Fix: conversation_participants was missing GRANT for the authenticated role.
-- Without it, all SELECT/INSERT/DELETE fail with "permission denied" before RLS even runs.
-- Run this in the Supabase SQL Editor.

-- 1. Grant table permissions
grant select, insert, delete on public.conversation_participants to authenticated;

-- 2. Fix INSERT policy: original policy required is_conversation_member() to be true,
--    creating a circular dependency — you needed a row to insert your first row.
--    New policy: conversation creator can always add participants.
drop policy if exists "conv_participants_insert" on public.conversation_participants;

create policy "conv_participants_insert" on public.conversation_participants
    for insert with check (
        exists (
            select 1 from public.conversations c
            where c.id = conversation_id
            and c.user_from = (select id from public.users where auth_id = auth.uid() limit 1)
        )
        or public.is_conversation_member(
            conversation_id,
            (select id from public.users where auth_id = auth.uid() limit 1)
        )
    );

-- 3. Backfill creator rows for conversations that have no participant rows yet
insert into public.conversation_participants (conversation_id, user_id)
select id, user_from
from public.conversations c
where not exists (
    select 1 from public.conversation_participants cp where cp.conversation_id = c.id
)
on conflict (conversation_id, user_id) do nothing;

-- 4. Backfill user_to for old-style 1:1 conversations
insert into public.conversation_participants (conversation_id, user_id)
select id, user_to
from public.conversations c
where user_to is not null
and not exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = c.id and cp.user_id = c.user_to
)
on conflict (conversation_id, user_id) do nothing;
