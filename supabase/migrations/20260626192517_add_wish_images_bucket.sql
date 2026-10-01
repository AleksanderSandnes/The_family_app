INSERT INTO storage.buckets (id, name, public)
VALUES ('wish-images', 'wish-images', true)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY "Wish images: authenticated read" ON storage.objects
  FOR SELECT USING (bucket_id = 'wish-images' AND auth.uid() IS NOT NULL);

CREATE POLICY "Wish images: authenticated write" ON storage.objects
  FOR INSERT WITH CHECK (bucket_id = 'wish-images' AND auth.uid() IS NOT NULL);

CREATE POLICY "Wish images: authenticated update" ON storage.objects
  FOR UPDATE USING (bucket_id = 'wish-images' AND auth.uid() IS NOT NULL);

CREATE POLICY "Wish images: authenticated delete" ON storage.objects
  FOR DELETE USING (bucket_id = 'wish-images' AND auth.uid() IS NOT NULL);;
