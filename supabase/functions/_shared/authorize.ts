// These notification jobs are server-to-server endpoints, never client RPCs.
export function authorizeJob(req: Request): Response | null {
  if (req.method !== "POST") {
    return new Response("method not allowed", { status: 405, headers: { Allow: "POST" } });
  }
  const expected = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const actual = req.headers.get("Authorization");
  if (!expected || actual !== `Bearer ${expected}`) {
    return new Response("unauthorized", { status: 401 });
  }
  return null;
}
