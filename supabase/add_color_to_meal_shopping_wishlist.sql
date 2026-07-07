-- Per-item colour for meal plans, shopping lists, and wishlists (0xRRGGBB int, nullable →
-- falls back to the feature accent). Chosen via the shared colour picker on create/edit.
alter table public.meal_plans add column if not exists color integer;
alter table public.shopping_lists add column if not exists color integer;
alter table public.wishlists add column if not exists color integer;
