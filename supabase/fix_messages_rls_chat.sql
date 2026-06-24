-- Fix messages RLS and add self-repair RPC for conversation membership.
-- Run this in the Supabase SQL Editor.
-- Safe to re-run: uses CREATE OR REPLACE, DROP IF EXISTS before CREATE.

-- ────────────────────────────────────────────────────────────
-- 1. Update messages_access — add creator fallback
--    Mirrors the creator fallback in conversations_access so the
--    conversation creator can always send/receive messages even if
--    conversation_participants rows are missing (e.g. conversations
--    created before the participant migration).
-- ────────────────────────────────────────────────────────────
drop policy if exists "messages_access" on public.messages;

create policy "messages_access" on public.messages
    for all using (
        public.is_conversation_member(
            conversation_id,
            (select id from public.users where auth_id = auth.uid() limit 1)
        )
        or exists (
            select 1 from public.conversations c
            where c.id = conversation_id
            and c.user_from = (select id from public.users where auth_id = auth.uid() limit 1)
        )
    );

-- ────────────────────────────────────────────────────────────
-- 2. Self-repair RPC: any family member can add themselves to a
--    conversation that belongs to their family.
--    SECURITY DEFINER so it bypasses RLS for the insert.
-- ────────────────────────────────────────────────────────────
create or replace function public.ensure_conversation_participant(conv_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  me_id     uuid;
  me_family uuid;
  conv_family uuid;
begin
  select id, family_id into me_id, me_family
  from public.users where auth_id = auth.uid() limit 1;

  if me_id is null then return; end if;

  select family_id into conv_family
  from public.conversations where id = conv_id;

  -- Only allow self-add if the conversation belongs to the same family
  if me_family is null or conv_family is null or me_family != conv_family then
    return;
  end if;

  insert into public.conversation_participants (conversation_id, user_id)
  values (conv_id, me_id)
  on conflict (conversation_id, user_id) do nothing;
end;
$$;
