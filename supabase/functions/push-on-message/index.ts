// Instant chat push. Invoked by a Database Webhook on `public.messages` INSERT.
// Resolves the conversation's participants (minus the sender), filters to those who still
// have notifications enabled, and fans a data-only push out to their device tokens.
//
// See supabase/functions/README.md for the webhook wiring.
import { serviceClient } from "../_shared/client.ts";
import { sendPushToTokens } from "../_shared/fcm.ts";

Deno.serve(async (req) => {
  try {
    const body = await req.json();
    // Supabase DB webhook payload: { type, table, schema, record, old_record }.
    const msg = body.record ?? body;
    if (!msg?.conversation_id) {
      return new Response("ignored: no conversation_id", { status: 200 });
    }

    const supabase = serviceClient();

    const [{ data: conversation }, { data: participants }, { data: sender }] = await Promise.all([
      supabase.from("conversations").select("id, name, image_uri").eq("id", msg.conversation_id)
        .maybeSingle(),
      supabase.from("conversation_participants").select("user_id").eq("conversation_id", msg.conversation_id),
      supabase.from("users").select("name").eq("id", msg.user_from).maybeSingle(),
    ]);

    const recipientIds = (participants ?? [])
      .map((p) => p.user_id)
      .filter((id) => id !== msg.user_from);
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
      .select("token")
      .in("user_id", enabledIds);
    const tokens = (tokenRows ?? []).map((t) => t.token);
    if (tokens.length === 0) return new Response("no tokens", { status: 200 });

    const preview = msg.message_type === "image"
      ? "📷 Image"
      : msg.message_type === "voice"
      ? "🎤 Voice message"
      : (msg.text ?? "");

    await sendPushToTokens(supabase, tokens, {
      type: "message",
      conversationId: String(msg.conversation_id),
      conversationName: conversation?.name ?? "",
      imageUri: conversation?.image_uri ?? "",
      messageId: String(msg.id ?? ""),
      messageType: msg.message_type ?? "text",
      text: preview,
      senderId: String(msg.user_from ?? ""),
      senderName: sender?.name ?? "Family member",
    });

    return new Response("ok", { status: 200 });
  } catch (e) {
    console.error("push-on-message error", e);
    return new Response("error", { status: 500 });
  }
});
