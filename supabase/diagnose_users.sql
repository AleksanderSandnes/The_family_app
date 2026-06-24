-- Diagnostic step 2: dump every user's current family_id and auth_id.
-- Run in the Supabase SQL Editor and paste the result back.
--
-- What we're checking:
--   * Are the two test users in the SAME family (matching family_id)?
--   * Is family_id NULL for the user whose newly-created rows came out with
--     family_id = null (User B = c91a5c7c-...)?
--   * Is auth_id populated on both rows?
--
-- Cross-reference owner ids from diagnose_family_sync.sql:
--   User A = c38ab3b3-56a8-4324-953e-39e4cc3a63fb
--   User B = c91a5c7c-fbc5-4336-9c6f-abd1d88299d9

select id, email, auth_id, family_id, name
from public.users
order by family_id nulls first;
