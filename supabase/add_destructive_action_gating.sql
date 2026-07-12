-- Destructive actions on shared family data become creator-or-admin-only (2026-07 design
-- decision): any member can create and edit, but deleting a whole shared container —
-- shopping list, calendar event, meal plan, conversation — is restricted to the row's
-- creator or the family admin. Wishlists and birthdays were already creator-gated.
-- Safe to re-run.

-- True when the calling user is their family's admin.
create or replace function public.i_am_family_admin()
returns boolean
language sql
stable
security definer
set search_path to 'public'
as $$
  select exists (
    select 1
    from public.families f
    where f.id = public.my_family_id()
      and f.admin_id = public.my_app_user_id()
  );
$$;

-- Meal plans were family-scoped with no creator column. Legacy rows stay null-creator;
-- null-creator plans are deletable by the family admin only. Older shipped clients don't
-- send created_by, so inserts must keep accepting null.
alter table public.meal_plans
  add column if not exists created_by uuid references public.users(id) on delete set null;

-- ── shopping_lists: split FOR ALL into read/write (family) vs delete (owner/admin) ──
drop policy if exists "shopping_lists_access" on public.shopping_lists;
drop policy if exists shopping_lists_select on public.shopping_lists;
drop policy if exists shopping_lists_insert on public.shopping_lists;
drop policy if exists shopping_lists_update on public.shopping_lists;
drop policy if exists shopping_lists_delete on public.shopping_lists;

create policy shopping_lists_select on public.shopping_lists
  for select using (
    owner_user_id = public.my_app_user_id()
    or family_id = public.my_family_id()
  );

create policy shopping_lists_insert on public.shopping_lists
  for insert with check (owner_user_id = public.my_app_user_id());

create policy shopping_lists_update on public.shopping_lists
  for update
  using (
    owner_user_id = public.my_app_user_id()
    or family_id = public.my_family_id()
  )
  with check (
    owner_user_id = public.my_app_user_id()
    or family_id = public.my_family_id()
  );

create policy shopping_lists_delete on public.shopping_lists
  for delete using (
    owner_user_id = public.my_app_user_id()
    or (family_id = public.my_family_id() and public.i_am_family_admin())
  );

-- ── calendar_events: preserve the privacy rule; gate delete to creator/admin ──
-- (admin may delete shared family events, never someone else's private event)
drop policy if exists calendar_events_access on public.calendar_events;
drop policy if exists calendar_events_select on public.calendar_events;
drop policy if exists calendar_events_insert on public.calendar_events;
drop policy if exists calendar_events_update on public.calendar_events;
drop policy if exists calendar_events_delete on public.calendar_events;

create policy calendar_events_select on public.calendar_events
  for select using (
    user_id = public.my_app_user_id()
    or (family_id = public.my_family_id() and coalesce(is_private, false) = false)
  );

create policy calendar_events_insert on public.calendar_events
  for insert with check (user_id = public.my_app_user_id());

create policy calendar_events_update on public.calendar_events
  for update
  using (
    user_id = public.my_app_user_id()
    or (family_id = public.my_family_id() and coalesce(is_private, false) = false)
  )
  with check (
    user_id = public.my_app_user_id()
    or (family_id = public.my_family_id() and coalesce(is_private, false) = false)
  );

create policy calendar_events_delete on public.calendar_events
  for delete using (
    user_id = public.my_app_user_id()
    or (
      family_id = public.my_family_id()
      and coalesce(is_private, false) = false
      and public.i_am_family_admin()
    )
  );

-- ── meal_plans: family read/write; delete by creator or admin ──
drop policy if exists "meal_plans_access" on public.meal_plans;
drop policy if exists meal_plans_select on public.meal_plans;
drop policy if exists meal_plans_insert on public.meal_plans;
drop policy if exists meal_plans_update on public.meal_plans;
drop policy if exists meal_plans_delete on public.meal_plans;

create policy meal_plans_select on public.meal_plans
  for select using (family_id = public.my_family_id());

create policy meal_plans_insert on public.meal_plans
  for insert with check (
    family_id = public.my_family_id()
    and (created_by is null or created_by = public.my_app_user_id())
  );

create policy meal_plans_update on public.meal_plans
  for update
  using (family_id = public.my_family_id())
  with check (family_id = public.my_family_id());

create policy meal_plans_delete on public.meal_plans
  for delete using (
    family_id = public.my_family_id()
    and (created_by = public.my_app_user_id() or public.i_am_family_admin())
  );

-- ── conversations: creator or an admin PARTICIPANT may delete ──
-- Read/write stays participant-scoped (add_conversation_participants.sql semantics — an
-- admin must NOT gain visibility into DMs they're not part of). Delete: the creator, or
-- the family admin when they are themselves a participant.
drop policy if exists "conversations_access" on public.conversations;
drop policy if exists conversations_select on public.conversations;
drop policy if exists conversations_insert on public.conversations;
drop policy if exists conversations_update on public.conversations;
drop policy if exists conversations_delete on public.conversations;

create policy conversations_select on public.conversations
  for select using (
    -- creator fallback (handles the instant between INSERT and first participant row)
    user_from = public.my_app_user_id()
    or public.is_conversation_member(id, public.my_app_user_id())
  );

create policy conversations_insert on public.conversations
  for insert with check (user_from = public.my_app_user_id());

create policy conversations_update on public.conversations
  for update
  using (
    user_from = public.my_app_user_id()
    or public.is_conversation_member(id, public.my_app_user_id())
  )
  with check (
    user_from = public.my_app_user_id()
    or public.is_conversation_member(id, public.my_app_user_id())
  );

create policy conversations_delete on public.conversations
  for delete using (
    user_from = public.my_app_user_id()
    or (
      public.is_conversation_member(id, public.my_app_user_id())
      and public.i_am_family_admin()
    )
  );
