import { authorizeJob } from "./authorize.ts";

function expectStatus(actual: Response | null, expected: number | null) {
  if ((actual?.status ?? null) !== expected) throw new Error("Unexpected authorization result");
}

Deno.test("notification jobs reject user, anon and missing credentials", () => {
  Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", "local-test-server-key");
  try {
    for (const token of [null, "Bearer fictional-user-jwt", "Bearer fictional-anon-jwt", "local-test-server-key"]) {
      const headers = token ? { Authorization: token } : undefined;
      expectStatus(authorizeJob(new Request("https://example.test", { method: "POST", headers })), 401);
    }
    expectStatus(authorizeJob(new Request("https://example.test", {
      method: "POST", headers: { Authorization: "Bearer local-test-server-key" },
    })), null);
    expectStatus(authorizeJob(new Request("https://example.test")), 405);
  } finally {
    Deno.env.delete("SUPABASE_SERVICE_ROLE_KEY");
  }
});

Deno.test("notification jobs fail closed without a configured server credential", () => {
  Deno.env.delete("SUPABASE_SERVICE_ROLE_KEY");
  expectStatus(authorizeJob(new Request("https://example.test", { method: "POST" })), 401);
});
