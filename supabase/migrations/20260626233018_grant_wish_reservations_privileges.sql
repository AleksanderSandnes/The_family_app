-- wish_reservations had RLS policies but no table-level grants, so the authenticated
-- role got "permission denied" on every insert/select/delete (Reserve button silently failed).
GRANT SELECT, INSERT, DELETE ON public.wish_reservations TO authenticated;;
