
-- user_locations was created without the standard Supabase role grants
GRANT SELECT, INSERT, UPDATE, DELETE ON public.user_locations TO authenticated;
GRANT SELECT ON public.user_locations TO anon;
;
