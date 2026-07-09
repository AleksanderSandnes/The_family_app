-- Calendar events gain (1) a private flag and (2) a per-event colour.
-- Private events are visible/editable only by their creator; shared events (the default)
-- stay family-visible and family-editable.
alter table public.calendar_events
  add column if not exists is_private boolean not null default false,
  add column if not exists color integer;

-- RLS: a row is visible/writable if it's yours, or it's a shared (non-private) event in
-- your family. The FOR ALL policy's USING doubles as the write-check (Postgres fallback),
-- so a member can't privatise someone else's event or read others' private events.
drop policy if exists calendar_events_access on public.calendar_events;
create policy calendar_events_access on public.calendar_events
  for all
  using (
    user_id = public.my_app_user_id()
    or (family_id = public.my_family_id() and coalesce(is_private, false) = false)
  );
