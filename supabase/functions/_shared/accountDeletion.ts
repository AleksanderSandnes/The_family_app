// Deletes the calling user's account: their uploaded media, their auth user (which
// cascades to public.users and every row they own), and their family once it is empty.
// Shared family containers are handed to a remaining member first.
// Dependencies are injected so the flow is unit-testable without a live project.

export interface MediaObject {
  bucket_id: string;
  name: string;
}

export interface AccountDeletionStore {
  /** Resolves the verified auth user id for a bearer token, or null when invalid. */
  userIdForToken(token: string): Promise<string | null>;
  familyOf(authId: string): Promise<string | null>;
  mediaOf(authId: string): Promise<MediaObject[]>;
  removeMedia(bucket: string, names: string[]): Promise<void>;
  /** Hands shared family containers (group chats, admin role, family lists) to a remaining member. */
  prepareDeletion(authId: string): Promise<void>;
  deleteAuthUser(authId: string): Promise<void>;
  purgeFamilyIfEmpty(familyId: string): Promise<void>;
}

const REMOVE_BATCH = 100;
const CONFIRMATION = "DELETE_MY_ACCOUNT";

function bearer(req: Request): string | null {
  const header = req.headers.get("Authorization") ?? "";
  const match = /^Bearer ([A-Za-z0-9._-]+)$/.exec(header);
  return match ? match[1] : null;
}

async function confirmed(req: Request): Promise<boolean> {
  if (
    req.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !==
      "application/json"
  ) {
    return false;
  }
  const text = await req.text();
  if (text.length > 1024) return false;
  try {
    return JSON.parse(text)?.confirm === CONFIRMATION;
  } catch {
    return false;
  }
}

export async function handleAccountDeletion(
  req: Request,
  store: AccountDeletionStore,
): Promise<Response> {
  if (req.method !== "POST") {
    return new Response("method not allowed", {
      status: 405,
      headers: { Allow: "POST" },
    });
  }
  const token = bearer(req);
  const authId = token ? await store.userIdForToken(token) : null;
  if (!authId) return new Response("unauthorized", { status: 401 });
  if (!(await confirmed(req))) {
    return new Response("confirmation required", { status: 400 });
  }

  // Storage first: if it fails the account still exists and the user can retry.
  const familyId = await store.familyOf(authId);
  const media = await store.mediaOf(authId);
  const byBucket = new Map<string, string[]>();
  for (const object of media) {
    byBucket.set(object.bucket_id, [
      ...(byBucket.get(object.bucket_id) ?? []),
      object.name,
    ]);
  }
  for (const [bucket, names] of byBucket) {
    for (let i = 0; i < names.length; i += REMOVE_BATCH) {
      await store.removeMedia(bucket, names.slice(i, i + REMOVE_BATCH));
    }
  }
  await store.prepareDeletion(authId);
  await store.deleteAuthUser(authId);
  if (familyId) await store.purgeFamilyIfEmpty(familyId);
  return new Response(null, { status: 204 });
}
