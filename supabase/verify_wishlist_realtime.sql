-- Wishlist sharing/reads work but live realtime push does not. That means the
-- wishlists table is not in the realtime publication. Run this to check + fix.

-- 1. Which tables are currently published for realtime? (look for 'wishlists')
select tablename
from pg_publication_tables
where pubname = 'supabase_realtime' and schemaname = 'public'
order by tablename;

-- 2. Ensure wishlists is published with full row images (idempotent).
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

-- 3. Confirm it is now present (wishlists should appear in this list).
select tablename
from pg_publication_tables
where pubname = 'supabase_realtime' and schemaname = 'public'
order by tablename;
