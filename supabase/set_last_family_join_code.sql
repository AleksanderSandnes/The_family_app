-- Add last_used_at to family join codes for auditing
ALTER TABLE public.families ADD COLUMN IF NOT EXISTS last_join_used_at TIMESTAMPTZ;
