DROP POLICY IF EXISTS "families_update_member" ON families;

CREATE POLICY "families_update_member" ON families
  FOR UPDATE USING (
    id = (SELECT family_id FROM users WHERE auth_id = auth.uid() LIMIT 1)
  );;
