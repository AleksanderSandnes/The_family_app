-- D6b: wishlist reserve/claim, hidden from the wishlist owner (surprise-preserving).
-- Reservations live in their own table so RLS (which is row-level) can exclude the owner
-- entirely — the owner's queries return zero reservation rows for their own wishlists.
-- Safe to re-run.

create table if not exists public.wish_reservations (
    id uuid primary key default gen_random_uuid(),
    wish_id uuid not null references public.wishes (id) on delete cascade,
    reserved_by uuid not null references public.users (id) on delete cascade,
    created_at timestamptz not null default now(),
    unique (wish_id) -- at most one reservation per wish
);

create index if not exists idx_wish_reservations_wish on public.wish_reservations (wish_id);

alter table public.wish_reservations enable row level security;

-- A family member who is NOT the wishlist owner can see reservations on that wishlist.
-- The owner is excluded, so they never learn what's been claimed.
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

-- Only a non-owner family member may reserve, and only as themselves.
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

-- A member can release (un-reserve) their own reservation.
drop policy if exists wish_reservations_delete on public.wish_reservations;
create policy wish_reservations_delete on public.wish_reservations
for delete using (
    reserved_by = (select id from public.users where auth_id = auth.uid() limit 1)
);

-- Live updates so members see reservations appear/disappear without a refresh.
alter table public.wish_reservations replica identity full;
do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and tablename = 'wish_reservations'
    ) then
        alter publication supabase_realtime add table public.wish_reservations;
    end if;
end $$;
