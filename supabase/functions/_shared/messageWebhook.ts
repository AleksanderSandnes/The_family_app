const MAX_BODY_BYTES = 64 * 1024;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export class WebhookInputError extends Error {
  constructor(public status: number) {
    super("Invalid message webhook request");
  }
}

export interface MessageRecord {
  id: string;
  conversation_id: string;
  user_from: string;
  message_type: "text" | "image" | "voice" | "system";
  text: string;
}

/** Bound the stream itself: Content-Length can be absent or dishonest. */
export async function readMessageWebhook(req: Request): Promise<MessageRecord> {
  if (
    req.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !==
      "application/json"
  ) {
    throw new WebhookInputError(415);
  }
  if (!req.body) throw new WebhookInputError(400);
  const reader = req.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_BODY_BYTES) {
        await reader.cancel();
        throw new WebhookInputError(413);
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.length;
  }
  let body;
  try {
    body = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
  } catch {
    throw new WebhookInputError(400);
  }
  if (
    body?.type !== "INSERT" || body?.table !== "messages" ||
    body?.schema !== "public"
  ) {
    throw new WebhookInputError(400);
  }
  const record = body.record;
  if (
    !record || [record.id, record.conversation_id, record.user_from].some(
      (id) => typeof id !== "string" || !UUID.test(id),
    ) || !["text", "image", "voice", "system"].includes(record.message_type) ||
    typeof record.text !== "string"
  ) {
    throw new WebhookInputError(400);
  }
  // Only retain the fields used to build notifications; ignore unrelated row metadata.
  return {
    id: record.id,
    conversation_id: record.conversation_id,
    user_from: record.user_from,
    message_type: record.message_type,
    text: record.text,
  };
}

/** Keep long messages within the push transport's limited payload budget. */
export function messagePreview(record: MessageRecord): string {
  if (record.message_type === "image") return "📷 Image";
  if (record.message_type === "voice") return "🎤 Voice message";
  const characters = Array.from(record.text);
  return characters.length > 200
    ? characters.slice(0, 200).join("") + "…"
    : record.text;
}
