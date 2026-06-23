-- Run this in the Supabase SQL Editor BEFORE building the updated app.
-- This migrates the existing schema to match the new Room-free version.

-- 1. Schema drift fixes
alter table public.conversations alter column user_to drop not null;
alter table public.conversations add column if not exists image_uri text;
alter table public.calendar_events add column if not exists icon text not null default 'schedule';

-- 2. Enable Realtime for chat tables
alter publication supabase_realtime add table public.messages;
alter publication supabase_realtime add table public.conversations;
alter table public.messages replica identity full;
alter table public.conversations replica identity full;

-- 3. RLS policies — replace the single users_self policy with family-scoped ones

drop policy if exists "users_self" on public.users;

create policy "users_access" on public.users
    for all using (
        auth_id = auth.uid()
        or (
            family_id is not null
            and family_id = (select family_id from public.users where auth_id = auth.uid() limit 1)
        )
    ) with check (auth_id = auth.uid());

create policy "families_select" on public.families
    for select using (
        id = (select family_id from public.users where auth_id = auth.uid() limit 1)
        or admin_id = (select id from public.users where auth_id = auth.uid() limit 1)
    );
create policy "families_insert" on public.families
    for insert with check (auth.uid() is not null);
create policy "families_update" on public.families
    for update using (
        admin_id = (select id from public.users where auth_id = auth.uid() limit 1)
    );

create policy "shopping_lists_access" on public.shopping_lists
    for all using (
        owner_user_id = (select id from public.users where auth_id = auth.uid() limit 1)
        or family_id = (select family_id from public.users where auth_id = auth.uid() limit 1)
    );

create policy "shopping_items_access" on public.shopping_items
    for all using (
        list_id in (
            select id from public.shopping_lists where
            owner_user_id = (select id from public.users where auth_id = auth.uid() limit 1)
            or family_id = (select family_id from public.users where auth_id = auth.uid() limit 1)
        )
    );

create policy "meal_plans_access" on public.meal_plans
    for all using (
        family_id = (select family_id from public.users where auth_id = auth.uid() limit 1)
    );

create policy "meal_plan_days_access" on public.meal_plan_days
    for all using (
        meal_plan_id in (
            select id from public.meal_plans where
            family_id = (select family_id from public.users where auth_id = auth.uid() limit 1)
        )
    );

create policy "calendar_events_access" on public.calendar_events
    for all using (
        user_id = (select id from public.users where auth_id = auth.uid() limit 1)
        or family_id = (select family_id from public.users where auth_id = auth.uid() limit 1)
    );

create policy "birthdays_access" on public.birthdays
    for all using (
        made_by_user_id = (select id from public.users where auth_id = auth.uid() limit 1)
        or family_id = (select family_id from public.users where auth_id = auth.uid() limit 1)
    );

create policy "wishlists_access" on public.wishlists
    for all using (
        owner_user_id = (select id from public.users where auth_id = auth.uid() limit 1)
    );

create policy "wishes_access" on public.wishes
    for all using (
        wishlist_id in (
            select id from public.wishlists where
            owner_user_id = (select id from public.users where auth_id = auth.uid() limit 1)
        )
    );

create policy "conversations_access" on public.conversations
    for all using (
        user_from = (select id from public.users where auth_id = auth.uid() limit 1)
        or family_id = (select family_id from public.users where auth_id = auth.uid() limit 1)
    );

create policy "messages_access" on public.messages
    for all using (
        conversation_id in (
            select id from public.conversations where
            user_from = (select id from public.users where auth_id = auth.uid() limit 1)
            or family_id = (select family_id from public.users where auth_id = auth.uid() limit 1)
        )
    );
