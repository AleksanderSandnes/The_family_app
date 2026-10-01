-- User-generated content moderation (Play UGC policy / App Store 1.2): users can
-- report a chat message and block another user. Apply after the baseline; safe to
-- re-run. Reports are written only through report_message(), which snapshots the
-- reported content server-side so a sender deleting the message cannot erase it.
create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated, service_role;

create table if not exists public.content_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_user_id uuid not null references public.users(id) on delete cascade,
  reported_user_id uuid references public.users(id) on delete set null,
  message_id uuid references public.messages(id) on delete set null,
  conversation_id uuid references public.conversations(id) on delete set null,
  reason text not null check (reason in ('spam', 'harassment', 'inappropriate', 'other')),
  details text check (char_length(details) <= 500),
  message_snapshot text check (char_length(message_snapshot) <= 4000),
  message_type text,
  status text not null default 'open' check (status in ('open', 'reviewed', 'actioned')),
  created_at timestamptz not null default now()
);
create index if not exists content_reports_status_idx on public.content_reports (status, created_at);
create index if not exists content_reports_reporter_idx on public.content_reports (reporter_user_id);
create index if not exists content_reports_reported_idx on public.content_reports (reported_user_id);
create index if not exists content_reports_message_idx on public.content_reports (message_id);
create index if not exists content_reports_conversation_idx on public.content_reports (conversation_id);
alter table public.content_reports enable row level security;

drop policy if exists content_reports_select_own on public.content_reports;
create policy content_reports_select_own on public.content_reports
  for select to authenticated
  using (reporter_user_id = (select public.my_app_user_id()));
revoke all on public.content_reports from anon, authenticated;
grant select on public.content_reports to authenticated;
grant all on public.content_reports to service_role;

create table if not exists public.user_blocks (
  blocker_id uuid not null references public.users(id) on delete cascade,
  blocked_id uuid not null references public.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);
create index if not exists user_blocks_blocked_idx on public.user_blocks (blocked_id);
alter table public.user_blocks enable row level security;

drop policy if exists user_blocks_select_own on public.user_blocks;
create policy user_blocks_select_own on public.user_blocks
  for select to authenticated using (blocker_id = (select public.my_app_user_id()));
drop policy if exists user_blocks_insert_own on public.user_blocks;
create policy user_blocks_insert_own on public.user_blocks
  for insert to authenticated with check (blocker_id = (select public.my_app_user_id()));
drop policy if exists user_blocks_delete_own on public.user_blocks;
create policy user_blocks_delete_own on public.user_blocks
  for delete to authenticated using (blocker_id = (select public.my_app_user_id()));
revoke all on public.user_blocks from anon, authenticated;
grant select, insert, delete on public.user_blocks to authenticated;
grant all on public.user_blocks to service_role;

create or replace function private.report_message(p_message_id uuid, p_reason text, p_details text)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog
as $$
declare
  me uuid := public.my_app_user_id();
  msg record;
  report_id uuid;
begin
  if me is null then
    raise exception 'Not authenticated' using errcode = '42501';
  end if;
  select m.id, m.conversation_id, m.user_from, m.text, m.message_type
    into msg from public.messages m where m.id = p_message_id;
  if msg.id is null or not exists (
    select 1 from public.conversation_participants cp
     where cp.conversation_id = msg.conversation_id and cp.user_id = me
  ) then
    raise exception 'Message not found' using errcode = 'P0002';
  end if;
  if msg.user_from = me then
    raise exception 'Cannot report your own message' using errcode = '22023';
  end if;
  insert into public.content_reports (
    reporter_user_id, reported_user_id, message_id, conversation_id,
    reason, details, message_snapshot, message_type
  ) values (
    me, msg.user_from, msg.id, msg.conversation_id,
    p_reason, nullif(btrim(left(p_details, 500)), ''), left(msg.text, 4000), msg.message_type
  ) returning id into report_id;
  return report_id;
end;
$$;

create or replace function public.report_message(p_message_id uuid, p_reason text, p_details text default null)
returns uuid
language sql
security invoker
set search_path = pg_catalog
as $$ select private.report_message(p_message_id, p_reason, p_details); $$;

revoke all on function private.report_message(uuid, text, text) from public, anon, authenticated;
grant execute on function private.report_message(uuid, text, text) to authenticated, service_role;
revoke all on function public.report_message(uuid, text, text) from public, anon;
grant execute on function public.report_message(uuid, text, text) to authenticated, service_role;
