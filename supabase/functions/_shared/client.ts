// Service-role Supabase client for Edge Functions. The service role bypasses RLS, so
// these functions can read every family's tokens/participants/preferences to fan out
// push notifications. SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are injected automatically
// into the Edge Function runtime.
import { createClient, SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

export function serviceClient(): SupabaseClient {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false } },
  );
}
