-- Share wishlists with the family (previously owner-only).
-- Run in the Supabase SQL Editor. Safe to re-run.

-- 1. Add family_id to wishlists (nullable; a wishlist created with no family
--    stays private until the owner joins a family).
alter table public.wishlists
    add column if not exists family_id uuid references public.families(id) on delete cascade;

create index if not exists idx_wishlists_family on public.wishlists(family_id);

-- 2. Backfill: stamp each existing wishlist with its owner's current family.
update public.wishlists w
set family_id = u.family_id
from public.users u
where w.owner_user_id = u.id
  and w.family_id is null
  and u.family_id is not null;

-- 3. Wishlists: owner OR same-family can access (mirrors shopping_lists).
drop policy if exists "wishlists_access" on public.wishlists;
create policy "wishlists_access" on public.wishlists
    for all using (
        owner_user_id = (select id from public.users where auth_id = auth.uid() limit 1)
        or family_id = (select family_id from public.users where auth_id = auth.uid() limit 1)
    );

-- 4. Wishes: visible if the parent wishlist is owned by me OR in my family.
drop policy if exists "wishes_access" on public.wishes;
create policy "wishes_access" on public.wishes
    for all using (
        wishlist_id in (
            select id from public.wishlists where
                owner_user_id = (select id from public.users where auth_id = auth.uid() limit 1)
                or family_id = (select family_id from public.users where auth_id = auth.uid() limit 1)
        )
    );

-- 5. Realtime: stream wishlist changes to subscribed family members.
alter table public.wishlists replica identity full;
do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'wishlists'
    ) then
        alter publication supabase_realtime add table public.wishlists;
    end if;
end $$;
