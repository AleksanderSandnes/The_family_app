-- Diagnostic for "data created by one family member is invisible to the other".
-- Run BOTH queries in the Supabase SQL Editor and paste the results back.
-- This tells us whether the bug is (1) users not in the same family,
-- (2) records carrying a null/mismatched family_id, or (3) an RLS/query issue.

-- 1. Are both users in the SAME family? Is auth_id set on both?
select id, email, auth_id, family_id, name
from public.users
order by family_id nulls first;

-- 2. Do the (non-syncing) records carry a family_id, and does it match the users'?
select 'shopping'  as src, id, owner_user_id   as owner, family_id from public.shopping_lists
union all select 'calendar', id, user_id,         family_id from public.calendar_events
union all select 'birthday', id, made_by_user_id, family_id from public.birthdays
union all select 'meal',     id, null::uuid,      family_id from public.meal_plans
union all select 'wishlist', id, owner_user_id,   null::uuid from public.wishlists
order by src;
