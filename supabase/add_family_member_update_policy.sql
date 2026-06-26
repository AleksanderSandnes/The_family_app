-- Allow any family member (not just the admin) to update the family row.
-- Needed so non-admin members can change the family photo.
-- Postgres permissive policies are OR'd, so this adds to the existing admin policy.
-- Safe to re-run.

DROP POLICY IF EXISTS "families_update_member" ON families;

CREATE POLICY "families_update_member" ON families
  FOR UPDATE USING (
    id = (SELECT family_id FROM users WHERE auth_id = auth.uid() LIMIT 1)
  );
