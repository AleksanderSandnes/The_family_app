-- Explicit recovery path, not a replacement for the recorded migration journal.
-- Run with psql -v ON_ERROR_STOP=1 -f supabase/baseline/restore.sql against an
-- EMPTY isolated Supabase database. This rejects an existing application schema.
\set ON_ERROR_STOP on
do $$
begin
  if to_regclass('public.users') is not null then
    raise exception 'Recovery requires an empty application database';
  end if;
end;
$$;
\ir 20260930_public.sql
\ir ../security/function_access.sql
\ir ../security/storage_writes.sql
\ir ../security/storage_reads.sql
\ir ../security/account_deletion.sql
\ir ../security/moderation.sql
\ir platform.sql
-- Push and daily-reminder delivery require separate secure Vault/cron setup.
-- No external notification endpoint is installed or called by this restore.
-- Security files commit their own stages. Discard the isolated database if any
-- stage fails; this entry point is not an atomic production upgrade.
