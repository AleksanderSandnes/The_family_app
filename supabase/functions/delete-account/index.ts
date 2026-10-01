// Self-service account deletion, called by signed-in Android/iOS clients.
// Gateway JWT verification stays on; the token is re-validated with Auth so only the
// caller's own account can be deleted. See supabase/security/account_deletion.sql.
import { serviceClient } from "../_shared/client.ts";
import { handleAccountDeletion } from "../_shared/accountDeletion.ts";
import { supabaseAccountDeletionStore } from "../_shared/accountDeletionStore.ts";

Deno.serve(async (req) => {
  try {
    return await handleAccountDeletion(
      req,
      supabaseAccountDeletionStore(serviceClient()),
    );
  } catch {
    return new Response("account deletion failed", { status: 500 });
  }
});
