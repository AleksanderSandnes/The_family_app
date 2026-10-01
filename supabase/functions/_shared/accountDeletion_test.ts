import {
  type AccountDeletionStore,
  handleAccountDeletion,
  type MediaObject,
} from "./accountDeletion.ts";

const AUTH_ID = "40000000-0000-0000-0000-000000000001";
const FAMILY_ID = "50000000-0000-0000-0000-000000000001";

function fakeStore(
  media: MediaObject[] = [],
  family: string | null = FAMILY_ID,
  failRemoval = false,
) {
  const calls: string[] = [];
  const store: AccountDeletionStore = {
    userIdForToken: (token) =>
      Promise.resolve(token === "valid.token" ? AUTH_ID : null),
    familyOf: () => Promise.resolve(family),
    mediaOf: () => Promise.resolve(media),
    removeMedia: (bucket, names) => {
      calls.push(`remove:${bucket}:${names.length}`);
      return failRemoval
        ? Promise.reject(new Error("storage"))
        : Promise.resolve();
    },
    prepareDeletion: (id) => {
      calls.push(`prepare:${id}`);
      return Promise.resolve();
    },
    deleteAuthUser: (id) => {
      calls.push(`delete:${id}`);
      return Promise.resolve();
    },
    purgeFamilyIfEmpty: (id) => {
      calls.push(`purge:${id}`);
      return Promise.resolve();
    },
  };
  return { store, calls };
}

function request(
  body: unknown = { confirm: "DELETE_MY_ACCOUNT" },
  token: string | null = "valid.token",
  method = "POST",
  contentType = "application/json",
) {
  const headers: Record<string, string> = { "Content-Type": contentType };
  if (token) headers.Authorization = `Bearer ${token}`;
  return new Request("https://example.test", {
    method,
    headers,
    body: method === "GET" ? undefined : JSON.stringify(body),
  });
}

function assertEquals(actual: unknown, expected: unknown) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(
      `Expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`,
    );
  }
}

Deno.test("account deletion removes media in batches, then the user, then an empty family", async () => {
  const media = [
    ...Array.from({ length: 150 }, (_, i) => ({
      bucket_id: "chat-media",
      name: `c/${AUTH_ID}/${i}.jpg`,
    })),
    { bucket_id: "avatars", name: `${AUTH_ID}/avatar.jpg` },
  ];
  const { store, calls } = fakeStore(media);
  const response = await handleAccountDeletion(request(), store);
  assertEquals(response.status, 204);
  assertEquals(calls, [
    "remove:chat-media:100",
    "remove:chat-media:50",
    "remove:avatars:1",
    `prepare:${AUTH_ID}`,
    `delete:${AUTH_ID}`,
    `purge:${FAMILY_ID}`,
  ]);
});

Deno.test("account deletion skips family cleanup for users without a family", async () => {
  const { store, calls } = fakeStore([], null);
  assertEquals((await handleAccountDeletion(request(), store)).status, 204);
  assertEquals(calls, [`prepare:${AUTH_ID}`, `delete:${AUTH_ID}`]);
});

Deno.test("account deletion rejects non-POST methods", async () => {
  const { store, calls } = fakeStore();
  const response = await handleAccountDeletion(
    request(undefined, "valid.token", "GET"),
    store,
  );
  assertEquals(response.status, 405);
  assertEquals(response.headers.get("Allow"), "POST");
  assertEquals(calls, []);
});

Deno.test("account deletion rejects missing, malformed and unknown tokens", async () => {
  for (const token of [null, "not a token", "unknown.token"]) {
    const { store, calls } = fakeStore();
    assertEquals(
      (await handleAccountDeletion(request(undefined, token), store)).status,
      401,
    );
    assertEquals(calls, []);
  }
});

Deno.test("account deletion requires an explicit JSON confirmation", async () => {
  const bodies: [unknown, string][] = [
    [{}, "application/json"],
    [{ confirm: true }, "application/json"],
    [{ confirm: "DELETE_MY_ACCOUNT" }, "text/plain"],
    [
      { confirm: "DELETE_MY_ACCOUNT", padding: "x".repeat(2000) },
      "application/json",
    ],
  ];
  for (const [body, type] of bodies) {
    const { store, calls } = fakeStore();
    const response = await handleAccountDeletion(
      request(body, "valid.token", "POST", type),
      store,
    );
    assertEquals(response.status, 400);
    assertEquals(calls, []);
  }
  const { store, calls } = fakeStore();
  const invalidJson = new Request("https://example.test", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: "Bearer valid.token",
    },
    body: "{",
  });
  assertEquals((await handleAccountDeletion(invalidJson, store)).status, 400);
  assertEquals(calls, []);
});

Deno.test("account deletion keeps the account when media removal fails", async () => {
  const { store, calls } = fakeStore(
    [{ bucket_id: "avatars", name: `${AUTH_ID}/a.jpg` }],
    FAMILY_ID,
    true,
  );
  let failed = false;
  try {
    await handleAccountDeletion(request(), store);
  } catch {
    failed = true;
  }
  assertEquals(failed, true);
  assertEquals(calls, ["remove:avatars:1"]);
});
