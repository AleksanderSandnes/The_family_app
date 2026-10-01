create table if not exists public.wish_reservations (
    id uuid primary key default gen_random_uuid(),
    wish_id uuid not null references public.wishes (id) on delete cascade,
    reserved_by uuid not null references public.users (id) on delete cascade,
    created_at timestamptz not null default now(),
    unique (wish_id)
);

create index if not exists idx_wish_reservations_wish on public.wish_reservations (wish_id);

alter table public.wish_reservations enable row level security;

drop policy if exists wish_reservations_select on public.wish_reservations;
create policy wish_reservations_select on public.wish_reservations
for select using (
    exists (
        select 1
        from public.wishes w
        join public.wishlists wl on wl.id = w.wishlist_id
        where w.id = wish_reservations.wish_id
          and wl.family_id = (select family_id from public.users where auth_id = auth.uid() limit 1)
          and wl.owner_user_id <> (select id from public.users where auth_id = auth.uid() limit 1)
    )
);

drop policy if exists wish_reservations_insert on public.wish_reservations;
create policy wish_reservations_insert on public.wish_reservations
for insert with check (
    reserved_by = (select id from public.users where auth_id = auth.uid() limit 1)
    and exists (
        select 1
        from public.wishes w
        join public.wishlists wl on wl.id = w.wishlist_id
        where w.id = wish_reservations.wish_id
          and wl.family_id = (select family_id from public.users where auth_id = auth.uid() limit 1)
          and wl.owner_user_id <> (select id from public.users where auth_id = auth.uid() limit 1)
    )
);

drop policy if exists wish_reservations_delete on public.wish_reservations;
create policy wish_reservations_delete on public.wish_reservations
for delete using (
    reserved_by = (select id from public.users where auth_id = auth.uid() limit 1)
);

alter table public.wish_reservations replica identity full;
do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and tablename = 'wish_reservations'
    ) then
        alter publication supabase_realtime add table public.wish_reservations;
    end if;
end $$;;
