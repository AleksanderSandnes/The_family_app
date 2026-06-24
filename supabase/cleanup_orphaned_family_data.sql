-- Heal rows that were created with family_id = null while a user's app had a
-- stale "no family" state (the getUser null-cache bug, now fixed in code).
-- These rows are only visible to their owner; re-stamping them with the owner's
-- current family makes them shared again.
--
-- SAFE: only touches rows where family_id IS NULL, and only sets it to the
-- owner's CURRENT family. Rows whose owner has no family are left untouched.
-- Review the SELECT first, then run the UPDATEs.

-- Preview what will change:
select 'shopping' src, s.id, s.owner_user_id, u.family_id as will_set
from public.shopping_lists s join public.users u on u.id = s.owner_user_id
where s.family_id is null and u.family_id is not null
union all
select 'calendar', c.id, c.user_id, u.family_id
from public.calendar_events c join public.users u on u.id = c.user_id
where c.family_id is null and u.family_id is not null
union all
select 'birthday', b.id, b.made_by_user_id, u.family_id
from public.birthdays b join public.users u on u.id = b.made_by_user_id
where b.family_id is null and u.family_id is not null;

-- Apply (uncomment to run):
-- update public.shopping_lists s set family_id = u.family_id
-- from public.users u where s.owner_user_id = u.id
--   and s.family_id is null and u.family_id is not null;

-- update public.calendar_events c set family_id = u.family_id
-- from public.users u where c.user_id = u.id
--   and c.family_id is null and u.family_id is not null;

-- update public.birthdays b set family_id = u.family_id
-- from public.users u where b.made_by_user_id = u.id
--   and b.family_id is null and u.family_id is not null;

-- NOTE: rows with a non-null family_id pointing at an OLD family (e.g. a family
-- you left) are intentionally left alone — they may still belong to that family's
-- other members. Delete them manually only if you are sure they are dead.
