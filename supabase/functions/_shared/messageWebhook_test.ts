import {
  messagePreview,
  readMessageWebhook,
  WebhookInputError,
} from "./messageWebhook.ts";

const record = {
  id: "10000000-0000-0000-0000-000000000001",
  conversation_id: "20000000-0000-0000-0000-000000000001",
  user_from: "30000000-0000-0000-0000-000000000001",
  message_type: "text" as const,
  text: "Fictional message",
};
const envelope = {
  type: "INSERT",
  table: "messages",
  schema: "public",
  record,
};

function request(body: unknown, contentType = "application/json") {
  return new Request("https://example.test", {
    method: "POST",
    headers: { "Content-Type": contentType },
    body: JSON.stringify(body),
  });
}

async function rejects(req: Request, status: number) {
  try {
    await readMessageWebhook(req);
  } catch (error) {
    if (error instanceof WebhookInputError && error.status === status) return;
    throw error;
  }
  throw new Error("Invalid webhook was accepted");
}

Deno.test("message webhook accepts database insert envelopes and retains only notification fields", async () => {
  const actual = await readMessageWebhook(
    request(
      { ...envelope, record: { ...record, media_url: "ignored" } },
      "application/json; charset=utf-8",
    ),
  );
  if (JSON.stringify(actual) !== JSON.stringify(record)) {
    throw new Error("Unexpected record");
  }
});

Deno.test("message webhook rejects wrong operations, tables, schemas and bare records", async () => {
  for (
    const body of [record, null, [], { ...envelope, type: "UPDATE" }, {
      ...envelope,
      table: "users",
    }, { ...envelope, schema: "private" }]
  ) {
    await rejects(request(body), 400);
  }
});

Deno.test("message webhook rejects malformed identities, message types and text", async () => {
  for (
    const change of [
      { id: "invalid" },
      { conversation_id: null },
      { user_from: 12 },
      { message_type: "unsupported" },
      { text: null },
    ]
  ) {
    await rejects(
      request({ ...envelope, record: { ...record, ...change } }),
      400,
    );
  }
});

Deno.test("message webhook rejects non-JSON media types and malformed JSON", async () => {
  await rejects(request(envelope, "text/plain"), 415);
  await rejects(
    new Request("https://example.test", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: "{",
    }),
    400,
  );
});

Deno.test("message webhook caps actual body bytes without relying on Content-Length", async () => {
  const bytes = new TextEncoder().encode(
    JSON.stringify({
      ...envelope,
      record: { ...record, text: "x".repeat(65536) },
    }),
  );
  let cancelled = false;
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(bytes);
    },
    cancel() {
      cancelled = true;
    },
  });
  await rejects(
    new Request("https://example.test", {
      method: "POST",
      headers: { "Content-Type": "application/json", "Content-Length": "1" },
      body,
    }),
    413,
  );
  if (!cancelled) throw new Error("Oversized stream was not cancelled");
});

Deno.test("notification previews bound Unicode text and preserve media labels", () => {
  const preview = messagePreview({ ...record, text: "😀".repeat(201) });
  if (preview !== "😀".repeat(200) + "…") {
    throw new Error("Preview split a Unicode character");
  }
  if (messagePreview(record) !== record.text) {
    throw new Error("Short text changed");
  }
  if (messagePreview({ ...record, message_type: "image" }) !== "📷 Image") {
    throw new Error("Image preview changed");
  }
  if (
    messagePreview({ ...record, message_type: "voice" }) !== "🎤 Voice message"
  ) throw new Error("Voice preview changed");
  if (messagePreview({ ...record, message_type: "system" }) !== record.text) {
    throw new Error("System preview changed");
  }
});
