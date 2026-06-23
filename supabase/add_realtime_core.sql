-- Enable Supabase Realtime for core family-shared tables.
-- Run this in the Supabase SQL Editor after schema.sql.

alter table public.shopping_lists  replica identity full;
alter table public.shopping_items  replica identity full;
alter table public.calendar_events replica identity full;
alter table public.birthdays       replica identity full;
alter table public.meal_plans      replica identity full;

alter publication supabase_realtime add table public.shopping_lists;
alter publication supabase_realtime add table public.shopping_items;
alter publication supabase_realtime add table public.calendar_events;
alter publication supabase_realtime add table public.birthdays;
alter publication supabase_realtime add table public.meal_plans;
