-- Realtime DELETE/UPDATE events need the full old record so per-conversation filters match
-- and clients can remove the right row (default replica identity ships only the PK).
alter table public.messages replica identity full;;
