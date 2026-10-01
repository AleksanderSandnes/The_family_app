// Validates the public Supabase client settings baked into a release build.
// Never prints values: errors name the problem only.
const PROJECT_REF = "bntcznvsbyshetndbxfa";

function jwtRole(key) {
  const parts = key.split(".");
  if (parts.length !== 3) return null;
  try {
    return JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8")).role ?? null;
  } catch {
    return null;
  }
}

export function validateClientConfig({ url, key }, ref = PROJECT_REF) {
  if (url !== `https://${ref}.supabase.co`) throw new Error("SUPABASE_URL is not the production project URL");
  if (!key) throw new Error("Supabase client key is missing");
  if (/^sb_secret_/.test(key)) throw new Error("Supabase client key is a secret key");
  if (/placeholder/i.test(key)) throw new Error("Supabase client key is a placeholder");
  if (/^sb_publishable_\S{8,}$/.test(key)) return;
  const role = jwtRole(key);
  if (role === "anon") return;
  throw new Error(role ? `Supabase client key has role ${role}, expected anon` : "Supabase client key is not a publishable or anon key");
}

if (import.meta.url === `file://${process.argv[1]}`) {
  try {
    validateClientConfig({ url: process.env.SUPABASE_URL, key: process.env.SUPABASE_ANON_KEY });
    console.log("Supabase client configuration is valid");
  } catch (error) {
    console.error(error.message);
    process.exit(1);
  }
}
