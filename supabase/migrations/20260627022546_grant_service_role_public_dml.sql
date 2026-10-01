-- Edge Functions authenticate as the `service_role` Postgres role (via the secret API key).
-- The original schema granted table DML only to `authenticated`, never to `service_role`,
-- so every server-side query from the push Edge Functions failed with 42501 permission denied.
-- Restore the standard Supabase grant: service_role has full access to public objects
-- (it bypasses RLS and is only ever used server-side with the secret key).
-- Authorized by the user.

grant all on all tables in schema public to service_role;
grant all on all sequences in schema public to service_role;
grant all on all routines in schema public to service_role;

alter default privileges in schema public grant all on tables to service_role;
alter default privileges in schema public grant all on sequences to service_role;
alter default privileges in schema public grant all on routines to service_role;;
