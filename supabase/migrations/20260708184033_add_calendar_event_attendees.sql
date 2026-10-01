alter table public.calendar_events
  add column if not exists attendee_ids text[] not null default '{}';;
