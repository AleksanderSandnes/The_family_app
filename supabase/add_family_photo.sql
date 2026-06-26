-- Add photo_url column to families table for family profile photos.
-- Safe to re-run.

ALTER TABLE families ADD COLUMN IF NOT EXISTS photo_url TEXT;
