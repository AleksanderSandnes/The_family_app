alter table public.calendar_events
  add column if not exists is_private boolean not null default false,
  add column if not exists color integer;

drop policy if exists calendar_events_access on public.calendar_events;
create policy calendar_events_access on public.calendar_events
  for all
  using (
    user_id = public.my_app_user_id()
    or (family_id = public.my_family_id() and coalesce(is_private, false) = false)
  );;
