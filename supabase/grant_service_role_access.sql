-- Grant the `service_role` Postgres role full access to the public schema.
-- Safe to re-run.
--
-- WHY THIS EXISTS
-- The push Edge Functions (supabase/functions/push-on-message, daily-reminders) talk to
-- Postgres with the project's secret key, which resolves to the `service_role` Postgres role.
-- The original schema granted table DML (SELECT/INSERT/UPDATE/DELETE) only to `authenticated`
-- and relied on RLS — it never granted those privileges to `service_role`. As a result every
-- server-side query from the Edge Functions failed with:
--     42501  permission denied for table conversation_participants
-- and the functions silently found no recipients (HTTP 200, but no push was ever sent).
--
-- This restores the standard Supabase setup: `service_role` is a server-only role (it bypasses
-- RLS and is only ever used with the secret key, never exposed to clients) and is expected to
-- have full access to the public schema.

grant all on all tables in schema public to service_role;
grant all on all sequences in schema public to service_role;
grant all on all routines in schema public to service_role;

-- Make sure tables/sequences/routines created later also grant service_role automatically.
alter default privileges in schema public grant all on tables to service_role;
alter default privileges in schema public grant all on sequences to service_role;
alter default privileges in schema public grant all on routines to service_role;
