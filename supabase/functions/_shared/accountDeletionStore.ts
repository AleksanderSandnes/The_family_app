// Supabase-backed AccountDeletionStore. Requires a service-role client; the SQL
// helpers it calls are executable by service_role only
// (supabase/security/account_deletion.sql).
import type { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";
import type { AccountDeletionStore, MediaObject } from "./accountDeletion.ts";

function fail(step: string, error: unknown): never {
  // Log only the failing step; never tokens, ids or payloads.
  console.error(`delete-account: ${step} failed`);
  throw error instanceof Error ? error : new Error(step);
}

export function supabaseAccountDeletionStore(
  supabase: SupabaseClient,
): AccountDeletionStore {
  return {
    async userIdForToken(token) {
      const { data, error } = await supabase.auth.getUser(token);
      return error ? null : data.user?.id ?? null;
    },
    async familyOf(authId) {
      const { data, error } = await supabase.rpc("account_deletion_family", {
        target_auth_id: authId,
      });
      if (error) fail("family lookup", error);
      return (data as string | null) ?? null;
    },
    async mediaOf(authId) {
      const { data, error } = await supabase.rpc("account_deletion_media", {
        target_auth_id: authId,
      });
      if (error) fail("media lookup", error);
      return (data ?? []) as MediaObject[];
    },
    async removeMedia(bucket, names) {
      const { error } = await supabase.storage.from(bucket).remove(names);
      if (error) fail("media removal", error);
    },
    async prepareDeletion(authId) {
      const { error } = await supabase.rpc("prepare_account_deletion", {
        target_auth_id: authId,
      });
      if (error) fail("ownership transfer", error);
    },
    async deleteAuthUser(authId) {
      const { error } = await supabase.auth.admin.deleteUser(authId);
      if (error) fail("auth deletion", error);
    },
    async purgeFamilyIfEmpty(familyId) {
      const { error } = await supabase.rpc("purge_family_if_empty", {
        target_family_id: familyId,
      });
      if (error) fail("family cleanup", error);
    },
  };
}
