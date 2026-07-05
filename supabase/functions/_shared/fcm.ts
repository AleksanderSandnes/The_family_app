// FCM HTTP v1 sender for Supabase Edge Functions (Deno).
//
// Android pushes are DATA-ONLY messages — the Android client (FamilyMessagingService) builds
// the actual notification so it controls channel, styling, and de-dup, and so delivery
// works even when the app is killed. FCM data values must all be strings.
//
// iOS pushes carry a `notification` block + `apns` section instead: data-only messages are
// background-throttled on iOS, so APNs must display the alert itself. The data payload is
// still attached for deep-linking on tap. A token's `platform` column decides the shape;
// anything other than 'ios' gets the original byte-identical Android payload.
//
// Requires the `FCM_SERVICE_ACCOUNT` secret: the full service-account JSON downloaded from
// Firebase (Project settings → Service accounts → Generate new private key). The Firebase
// project id is read from that JSON, so no separate project-id secret is needed.
import { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

interface ServiceAccount {
  client_email: string;
  private_key: string;
  project_id: string;
  token_uri?: string;
}

function getServiceAccount(): ServiceAccount {
  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!raw) throw new Error("FCM_SERVICE_ACCOUNT secret is not set");
  return JSON.parse(raw) as ServiceAccount;
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const binary = atob(b64);
  const buf = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) buf[i] = binary.charCodeAt(i);
  return buf.buffer;
}

function base64url(data: Uint8Array | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// Cache the OAuth access token across invocations of a warm function instance.
let cachedToken: { token: string; exp: number } | null = null;

async function getAccessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.exp - 60 > now) return cachedToken.token;

  const aud = sa.token_uri ?? "https://oauth2.googleapis.com/token";
  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud,
    iat: now,
    exp: now + 3600,
  };
  const unsigned = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claims))}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = new Uint8Array(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)),
  );
  const jwt = `${unsigned}.${base64url(sig)}`;

  const res = await fetch(aud, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!res.ok) {
    throw new Error(`OAuth token exchange failed: ${res.status} ${await res.text()}`);
  }
  const json = await res.json();
  cachedToken = { token: json.access_token, exp: now + (json.expires_in ?? 3600) };
  return cachedToken.token;
}

/** One push destination: an FCM token plus the platform it was registered from. */
export interface PushTarget {
  token: string;
  platform?: string | null; // 'android' (default) | 'ios'
}

/** Visible alert content — required for iOS targets, ignored for Android (data-only). */
export interface PushAlert {
  title: string;
  body: string;
  /** APNs thread-id for notification grouping (e.g. the conversation id). */
  threadId?: string;
}

function buildMessage(
  target: PushTarget,
  data: Record<string, string>,
  alert?: PushAlert,
): Record<string, unknown> {
  if (target.platform !== "ios") {
    // Android: byte-identical to the original data-only payload.
    return { token: target.token, data, android: { priority: "high" } };
  }
  return {
    token: target.token,
    data,
    ...(alert ? { notification: { title: alert.title, body: alert.body } } : {}),
    apns: {
      headers: { "apns-push-type": "alert", "apns-priority": "10" },
      payload: {
        aps: {
          "mutable-content": 1,
          sound: "default",
          ...(alert?.threadId ? { "thread-id": alert.threadId } : {}),
        },
      },
    },
  };
}

/**
 * Sends a push to each target, shaped per platform (see file header). Tokens FCM reports
 * as unregistered/invalid are pruned from `device_push_tokens` so stale tokens don't
 * accumulate.
 */
export async function sendPushToTokens(
  supabase: SupabaseClient,
  targets: PushTarget[],
  data: Record<string, string>,
  alert?: PushAlert,
): Promise<void> {
  if (targets.length === 0) return;
  const sa = getServiceAccount();
  const accessToken = await getAccessToken(sa);
  const url = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;
  const stale: string[] = [];

  await Promise.all(
    targets.map(async (target) => {
      const res = await fetch(url, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ message: buildMessage(target, data, alert) }),
      });
      if (res.ok) return;
      // 404 UNREGISTERED, 400 invalid argument (bad/expired token) → prune.
      if (res.status === 404 || res.status === 400) {
        stale.push(target.token);
      } else {
        console.error(
          `FCM send failed (${res.status}) for ${target.token.slice(0, 12)}…: ${await res.text()}`,
        );
      }
    }),
  );

  if (stale.length > 0) {
    await supabase.from("device_push_tokens").delete().in("token", stale);
  }
}
