-- Fix: infinite recursion in users RLS policies
-- Drops ALL existing policies on users and recreates them cleanly.

-- Step 1: Drop every policy on users (regardless of name)
DO $$
DECLARE
  pol record;
BEGIN
  FOR pol IN
    SELECT policyname FROM pg_policies
    WHERE tablename = 'users' AND schemaname = 'public'
  LOOP
    EXECUTE 'DROP POLICY IF EXISTS "' || pol.policyname || '" ON public.users';
  END LOOP;
END $$;

-- Step 2: Helper function that bypasses RLS for family_id lookup
CREATE OR REPLACE FUNCTION public.my_family_id()
RETURNS uuid
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
STABLE
AS $$
  SELECT family_id FROM public.users WHERE auth_id = auth.uid() LIMIT 1;
$$;

-- Step 3: Recreate all policies without recursion
CREATE POLICY "users select" ON public.users
FOR SELECT USING (
  auth_id = auth.uid() OR
  (family_id IS NOT NULL AND family_id = my_family_id())
);

CREATE POLICY "users insert" ON public.users
FOR INSERT WITH CHECK (auth_id = auth.uid());

CREATE POLICY "users update" ON public.users
FOR UPDATE USING (auth_id = auth.uid());

CREATE POLICY "users delete" ON public.users
FOR DELETE USING (auth_id = auth.uid());
