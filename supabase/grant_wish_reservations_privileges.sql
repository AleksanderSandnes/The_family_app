-- add_wish_reservations.sql created the table and RLS policies but never granted
-- table-level privileges to the `authenticated` role, so every insert/select/delete
-- failed with "permission denied for table wish_reservations" (the Reserve button on
-- another member's wishlist silently did nothing). Grant the privileges the RLS
-- policies are written against. Safe to re-run.
GRANT SELECT, INSERT, DELETE ON public.wish_reservations TO authenticated;
