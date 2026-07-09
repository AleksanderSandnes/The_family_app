-- "Going with" — the family members the creator is attending a calendar event with.
-- Stored as an array of public.users.id values (text[]); shown in the day-list card.
alter table public.calendar_events
  add column if not exists attendee_ids text[] not null default '{}';
