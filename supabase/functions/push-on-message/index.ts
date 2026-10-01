// Instant chat push. Invoked by a Database Webhook on `public.messages` INSERT.
// Resolves the conversation's participants (minus the sender), filters to those who still
// have notifications enabled, and fans a data-only push out to their device tokens.
//
// See supabase/functions/README.md for the webhook wiring.
import { serviceClient } from "../_shared/client.ts";
import { sendPushToTokens } from "../_shared/fcm.ts";
import { authorizeJob } from "../_shared/authorize.ts";
import { messagePreview, readMessageWebhook, WebhookInputError } from "../_shared/messageWebhook.ts";

Deno.serve(async (req) => {
  const denied = authorizeJob(req);
  if (denied) return denied;
  try {
    const msg = await readMessageWebhook(req);

    const supabase = serviceClient();

    const [{ data: conversation }, { data: participants }, { data: sender }, { data: senderTokenRows }] =
      await Promise.all([
        supabase.from("conversations").select("id, name, image_uri").eq("id", msg.conversation_id)
          .maybeSingle(),
        supabase.from("conversation_participants").select("user_id").eq("conversation_id", msg.conversation_id),
        supabase.from("users").select("name").eq("id", msg.user_from).maybeSingle(),
        // The sender's own device tokens — never push a message back to the device that sent it.
        supabase.from("device_push_tokens").select("token").eq("user_id", msg.user_from),
      ]);

    // Recipients who blocked the sender get no push (user_blocks, supabase/security/moderation.sql).
    const { data: blocks } = await supabase
      .from("user_blocks")
      .select("blocker_id")
      .eq("blocked_id", msg.user_from);
    const blockedBy = new Set((blocks ?? []).map((b) => b.blocker_id));
    const recipientIds = (participants ?? [])
      .map((p) => p.user_id)
      .filter((id) => id !== msg.user_from && !blockedBy.has(id));
    if (recipientIds.length === 0) return new Response("no recipients", { status: 200 });

    // Respect each recipient's notification preference (mirrored from the client).
    const { data: recipients } = await supabase
      .from("users")
      .select("id, notifications_enabled")
      .in("id", recipientIds);
    const enabledIds = (recipients ?? [])
      .filter((u) => u.notifications_enabled !== false)
      .map((u) => u.id);
    if (enabledIds.length === 0) return new Response("all muted", { status: 200 });

    const { data: tokenRows } = await supabase
      .from("device_push_tokens")
      .select("token, platform")
      .in("user_id", enabledIds);

    // A single physical device can carry a recipient's token row (two accounts signed in on
    // one phone, or a rotated token), which would deliver the sender a banner for their own
    // message. Drop the sender's tokens, and dedup so a token shared by multiple recipients
    // is only pushed once.
    const senderTokens = new Set((senderTokenRows ?? []).map((t) => t.token));
    const seen = new Set<string>();
    const targets: { token: string; platform: string | null }[] = [];
    for (const t of tokenRows ?? []) {
      if (senderTokens.has(t.token) || seen.has(t.token)) continue;
      seen.add(t.token);
      targets.push({ token: t.token, platform: t.platform });
    }
    if (targets.length === 0) return new Response("no tokens", { status: 200 });

    const preview = messagePreview(msg);

    const senderName = sender?.name ?? "Family member";

    await sendPushToTokens(supabase, targets, {
      type: "message",
      conversationId: String(msg.conversation_id),
      conversationName: conversation?.name ?? "",
      imageUri: conversation?.image_uri ?? "",
      messageId: String(msg.id ?? ""),
      messageType: msg.message_type ?? "text",
      text: preview,
      senderId: String(msg.user_from ?? ""),
      senderName,
    }, {
      // Alert content for iOS targets; Android ignores this and builds its own notification.
      title: conversation?.name || senderName,
      body: conversation?.name ? `${senderName}: ${preview}` : preview,
      threadId: String(msg.conversation_id),
      category: "MESSAGE",
    });

    return new Response("ok", { status: 200 });
  } catch (error) {
    if (error instanceof WebhookInputError) {
      return new Response("invalid request", { status: error.status });
    }
    console.error("push-on-message failed");
    return new Response("error", { status: 500 });
  }
});
