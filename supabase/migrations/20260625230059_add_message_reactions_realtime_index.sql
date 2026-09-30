
-- Supabase Realtime requires an index on filtered columns for Postgres Changes
CREATE INDEX IF NOT EXISTS idx_message_reactions_conversation_id 
  ON public.message_reactions (conversation_id);
CREATE INDEX IF NOT EXISTS idx_message_reactions_message_id 
  ON public.message_reactions (message_id);
;
