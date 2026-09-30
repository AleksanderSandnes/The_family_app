
-- message_reactions was created without the standard Supabase role grants,
-- so Realtime's subscription_check_filters sees empty col_names for 'authenticated'
-- and raises "invalid column for filter conversation_id".
GRANT SELECT, INSERT, UPDATE, DELETE ON public.message_reactions TO authenticated;
GRANT SELECT ON public.message_reactions TO anon;
;
