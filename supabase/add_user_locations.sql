-- M10 Family Map — user_locations table
-- Run in Supabase SQL Editor

create table if not exists public.user_locations (
    user_id      uuid primary key references public.users(id) on delete cascade,
    family_id    uuid references public.families(id) on delete set null,
    lat          double precision not null,
    lng          double precision not null,
    display_name text not null default '',
    updated_at   timestamptz not null default now(),
    visible      boolean not null default false
);

alter table public.user_locations enable row level security;

-- Own row: full access (insert/update/delete)
create policy "user_locations_own" on public.user_locations
    for all using (
        user_id = (select id from public.users where auth_id = auth.uid() limit 1)
    ) with check (
        user_id = (select id from public.users where auth_id = auth.uid() limit 1)
    );

-- Family members: can read visible locations
create policy "user_locations_family_read" on public.user_locations
    for select using (
        visible = true
        and family_id is not null
        and family_id = (select family_id from public.users where auth_id = auth.uid() limit 1)
    );

-- Realtime
alter publication supabase_realtime add table public.user_locations;
alter table public.user_locations replica identity full;
