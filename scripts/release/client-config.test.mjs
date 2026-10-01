import assert from "node:assert/strict";
import { test } from "node:test";
import { validateClientConfig } from "./client-config.mjs";

const url = "https://bntcznvsbyshetndbxfa.supabase.co";
const jwt = (role) => `h.${Buffer.from(JSON.stringify({ role })).toString("base64url")}.s`;

test("accepts publishable and legacy anon keys", () => {
  validateClientConfig({ url, key: "sb_publishable_abcdefghijkl" });
  validateClientConfig({ url, key: jwt("anon") });
});

test("rejects secret, service-role, placeholder and malformed keys", () => {
  for (const key of ["sb_secret_abcdefghijkl", jwt("service_role"), "placeholder-anon-key", "plain", "", undefined]) {
    assert.throws(() => validateClientConfig({ url, key }), /key/i);
  }
});

test("rejects placeholder and other-project URLs", () => {
  for (const bad of ["https://placeholder.supabase.co", "https://xdttfrknoazcqcelieck.supabase.co", undefined, `${url}/`]) {
    assert.throws(() => validateClientConfig({ url: bad, key: jwt("anon") }), /URL/);
  }
});

test("errors never echo the key", () => {
  const key = jwt("service_role");
  assert.throws(() => validateClientConfig({ url, key }), (error) => !error.message.includes(key));
});
