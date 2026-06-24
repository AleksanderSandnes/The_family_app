-- Add icon column to shopping_lists (safe to re-run)
alter table public.shopping_lists
    add column if not exists icon text not null default 'shopping_cart';
