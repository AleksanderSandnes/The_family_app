-- Directional family relations ("relative to each viewer"): from_user_id's relation TO
-- to_user_id (e.g. Alice → Bob = "Dad"). Each viewer sets/edits their own perspective.
-- Safe to re-run.

-- Resolve the calling user's app id (public.users.id) from auth.uid().
create or replace function public.my_app_user_id()
returns uuid
language sql
stable
security definer
set search_path to 'public'
as $$
  select id from public.users where auth_id = auth.uid() limit 1;
$$;

create table if not exists public.family_relations (
    id           uuid primary key default gen_random_uuid(),
    family_id    uuid not null references public.families(id) on delete cascade,
    from_user_id uuid not null references public.users(id) on delete cascade,
    to_user_id   uuid not null references public.users(id) on delete cascade,
    relation     text not null,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    unique (from_user_id, to_user_id)
);

alter table public.family_relations enable row level security;

-- Base-table privileges (RLS still restricts rows). New tables don't grant these by default.
grant select, insert, update, delete on public.family_relations to authenticated;
grant select on public.family_relations to anon;
grant all on public.family_relations to service_role;

-- Everyone in the family can read relations (used for member profiles).
drop policy if exists "family_relations select" on public.family_relations;
create policy "family_relations select" on public.family_relations
  for select using (family_id = public.my_family_id());

-- You may only create/edit/delete YOUR OWN perspective (from_user_id = you).
drop policy if exists "family_relations insert" on public.family_relations;
create policy "family_relations insert" on public.family_relations
  for insert with check (
    from_user_id = public.my_app_user_id() and family_id = public.my_family_id()
  );

drop policy if exists "family_relations update" on public.family_relations;
create policy "family_relations update" on public.family_relations
  for update using (from_user_id = public.my_app_user_id());

drop policy if exists "family_relations delete" on public.family_relations;
create policy "family_relations delete" on public.family_relations
  for delete using (from_user_id = public.my_app_user_id());

-- Live updates in the family / member-profile views.
do $$ begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'family_relations'
  ) then
    alter publication supabase_realtime add table public.family_relations;
  end if;
end $$;
