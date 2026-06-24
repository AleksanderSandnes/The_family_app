-- Decisive test: is this a WRITE bug (new rows get family_id = null) or a
-- READ bug (new rows are fine, but reads/refresh fail to show them)?
--
-- Both users are confirmed in family e45acee2 RIGHT NOW, so we test the
-- current write path.
--
-- STEPS:
--   1. On ALEKSANDER's instance (the one that produced the null row),
--      create a brand-new shopping list in the app.
--   2. Run this query and paste the result.
--
-- INTERPRETATION:
--   * Newest row family_id = null         -> CURRENT WRITE BUG (getUser/familyId
--                                            is null at insert time). Fix write path.
--   * Newest row family_id = e45acee2...  -> the old nulls were STALE ARTIFACTS;
--                                            new writes are fine. Pivot to the
--                                            read/refresh/RLS side.

select owner_user_id, family_id, title, created_at
from public.shopping_lists
order by created_at desc
limit 5;
