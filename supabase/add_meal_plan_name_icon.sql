-- Adds name and icon fields to meal_plans.
-- Safe to re-run (ADD COLUMN IF NOT EXISTS).

ALTER TABLE public.meal_plans
    ADD COLUMN IF NOT EXISTS name TEXT NOT NULL DEFAULT '';

ALTER TABLE public.meal_plans
    ADD COLUMN IF NOT EXISTS icon TEXT NOT NULL DEFAULT 'restaurant';
