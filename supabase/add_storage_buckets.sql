-- Create storage buckets for profile avatars and group chat images.
-- Run this in the Supabase SQL Editor.
--
-- Avatar paths use the Supabase AUTH UUID (auth.uid()) as the folder,
-- which is what ProfileViewModel now writes to when uploading.
-- Group-image paths use the conversation UUID — any authenticated user can manage them.

INSERT INTO storage.buckets (id, name, public)
VALUES ('avatars', 'avatars', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO storage.buckets (id, name, public)
VALUES ('group-images', 'group-images', true)
ON CONFLICT (id) DO NOTHING;

-- Avatars: anyone can read; only the folder-owner (matching auth.uid()) can write.
CREATE POLICY "Avatars: public read" ON storage.objects
  FOR SELECT USING (bucket_id = 'avatars');

CREATE POLICY "Avatars: owner insert" ON storage.objects
  FOR INSERT WITH CHECK (
    bucket_id = 'avatars'
    AND auth.uid()::text = (storage.foldername(name))[1]
  );

CREATE POLICY "Avatars: owner update" ON storage.objects
  FOR UPDATE USING (
    bucket_id = 'avatars'
    AND auth.uid()::text = (storage.foldername(name))[1]
  );

CREATE POLICY "Avatars: owner delete" ON storage.objects
  FOR DELETE USING (
    bucket_id = 'avatars'
    AND auth.uid()::text = (storage.foldername(name))[1]
  );

-- Group images: any authenticated user can read and write.
CREATE POLICY "Group images: authenticated read" ON storage.objects
  FOR SELECT USING (bucket_id = 'group-images' AND auth.uid() IS NOT NULL);

CREATE POLICY "Group images: authenticated write" ON storage.objects
  FOR INSERT WITH CHECK (bucket_id = 'group-images' AND auth.uid() IS NOT NULL);

CREATE POLICY "Group images: authenticated update" ON storage.objects
  FOR UPDATE USING (bucket_id = 'group-images' AND auth.uid() IS NOT NULL);

CREATE POLICY "Group images: authenticated delete" ON storage.objects
  FOR DELETE USING (bucket_id = 'group-images' AND auth.uid() IS NOT NULL);
