// Real Auth/Storage/database test for account deletion on a DISPOSABLE localhost
// Supabase stack. Runs the production handler and Supabase-backed store with only
// fictional fixtures. Requires the captured schema, function_access.sql,
// storage_writes.sql and account_deletion.sql to be applied first.
//
//   deno run --allow-env --allow-net --allow-run supabase/tests/account_deletion_http.ts <workdir>
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { handleAccountDeletion } from "../functions/_shared/accountDeletion.ts";
import { supabaseAccountDeletionStore } from "../functions/_shared/accountDeletionStore.ts";

const workdir = Deno.args[0];
if (!workdir) {
  throw new Error("Usage: account_deletion_http.ts <supabase workdir>");
}
const status = JSON.parse(
  new TextDecoder().decode(
    (await new Deno.Command("supabase", {
      args: ["status", "--workdir", workdir, "-o", "json"],
      stderr: "null",
    }).output()).stdout,
  ),
);
const api = new URL(status.API_URL);
if (api.protocol !== "http:" || api.hostname !== "127.0.0.1") {
  throw new Error("Only a disposable localhost stack is permitted");
}
const projectId = /project_id\s*=\s*"([^"]+)"/.exec(
  await Deno.readTextFile(`${workdir}/supabase/config.toml`),
)?.[1];
if (!["family-media-db", "family-db-validation"].includes(projectId ?? "")) {
  throw new Error(
    "Use a disposable family-media-db or family-db-validation project",
  );
}

let checks = 0;
function check(condition: unknown, message: string) {
  if (!condition) throw new Error(message);
  checks++;
}

async function sql(statement: string): Promise<string> {
  const child = new Deno.Command("docker", {
    args: [
      "exec",
      "-i",
      `supabase_db_${projectId}`,
      "psql",
      "-U",
      "postgres",
      "-d",
      "postgres",
      "-v",
      "ON_ERROR_STOP=1",
      "-tA",
    ],
    stdin: "piped",
    stdout: "piped",
  }).spawn();
  const writer = child.stdin.getWriter();
  await writer.write(new TextEncoder().encode(statement));
  await writer.close();
  const { success, stdout } = await child.output();
  if (!success) throw new Error("SQL fixture failed");
  return new TextDecoder().decode(stdout).trim();
}

const service = createClient(status.API_URL, status.SERVICE_ROLE_KEY, {
  auth: { persistSession: false },
});
const store = supabaseAccountDeletionStore(service);

async function createUser() {
  const email = `delete-${crypto.randomUUID()}@example.test`;
  const password = `${crypto.randomUUID()}Ab1!`;
  const { data, error } = await service.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
  });
  if (error || !data.user) throw error ?? new Error("createUser");
  const client = createClient(status.API_URL, status.ANON_KEY, {
    auth: { persistSession: false },
  });
  const session = await client.auth.signInWithPassword({ email, password });
  if (session.error) throw session.error;
  return {
    aid: data.user.id,
    app: crypto.randomUUID(),
    email,
    token: session.data.session!.access_token,
    client,
  };
}

function deletion(token: string) {
  return new Request("http://127.0.0.1/delete-account", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ confirm: "DELETE_MY_ACCOUNT" }),
  });
}

async function exists(bucket: string, path: string): Promise<boolean> {
  const folder = path.split("/").slice(0, -1).join("/");
  const file = path.split("/").pop();
  const { data } = await service.storage.from(bucket).list(folder, {
    search: file,
  });
  return (data ?? []).some((o) => o.name === file);
}

const a = await createUser();
const b = await createUser();
const fam = crypto.randomUUID();
const conv = crypto.randomUUID();
const wl = crypto.randomUUID();
const list = crypto.randomUUID();
await sql(`
  insert into public.users(id,auth_id,name,email) values
    ('${a.app}','${a.aid}','Fictional Emma','${a.email}'),
    ('${b.app}','${b.aid}','Fictional Lars','${b.email}');
  insert into public.families(id,name,join_code,admin_id)
    values('${fam}','Fictional Nordmann ${fam}','${fam}','${a.app}');
  update public.users set family_id='${fam}' where id in ('${a.app}','${b.app}');
  insert into public.conversations(id,user_from,family_id) values('${conv}','${a.app}','${fam}');
  insert into public.conversation_participants(conversation_id,user_id)
    values('${conv}','${a.app}'),('${conv}','${b.app}');
  insert into public.wishlists(id,owner_user_id,family_id,name)
    values('${wl}','${a.app}','${fam}','Fictional wishlist');
  insert into public.messages(conversation_id,user_from,text)
    values('${conv}','${a.app}','Fictional hello'),('${conv}','${b.app}','Fictional reply');
  insert into public.shopping_lists(id,owner_user_id,family_id,title)
    values('${list}','${a.app}','${fam}','Fictional groceries');
`);

const pixels = new TextEncoder().encode("fictional-media");
const aObjects: [string, string][] = [
  ["avatars", `${a.aid}/avatar.jpg`],
  ["wish-images", `${a.app}/wish.jpg`],
  ["chat-media", `${conv}/${a.aid}/voice.m4a`],
];
const familyPhoto: [string, string] = [
  "group-images",
  `family-photos/${fam}/photo.jpg`,
];
const groupImage: [string, string] = ["group-images", `${conv}/group.jpg`];
const bAvatar: [string, string] = ["avatars", `${b.aid}/avatar.jpg`];
for (const [bucket, path] of [...aObjects, familyPhoto, groupImage]) {
  const { error } = await a.client.storage.from(bucket).upload(path, pixels, {
    upsert: true,
  });
  check(!error, `upload ${bucket} failed: ${error?.message}`);
}
{
  const { error } = await b.client.storage.from(bAvatar[0]).upload(
    bAvatar[1],
    pixels,
    { upsert: true },
  );
  check(!error, "upload for remaining member failed");
}

// Clients can never call the service-only helpers.
for (
  const [fn, args] of [
    ["account_deletion_media", { target_auth_id: b.aid }],
    ["account_deletion_family", { target_auth_id: b.aid }],
    ["purge_family_if_empty", { target_family_id: fam }],
    ["prepare_account_deletion", { target_auth_id: b.aid }],
  ] as const
) {
  const { error } = await a.client.rpc(fn, args);
  check(error, `${fn} must be denied to authenticated users`);
}

// Another account's token or a missing confirmation deletes nothing.
check(
  (await handleAccountDeletion(
    new Request("http://127.0.0.1", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${a.token}`,
        "Content-Type": "application/json",
      },
      body: "{}",
    }),
    store,
  )).status === 400,
  "unconfirmed deletion must be rejected",
);
check(
  (await handleAccountDeletion(deletion("forged.token.value"), store))
    .status === 401,
  "forged token must be rejected",
);

// Member A leaves: their data goes, the family and shared media stay for B.
check(
  (await handleAccountDeletion(deletion(a.token), store)).status === 204,
  "deletion of A",
);
for (const [bucket, path] of aObjects) {
  check(!(await exists(bucket, path)), `${bucket}/${path} must be removed`);
}
check(
  await exists(...familyPhoto),
  "family photo stays while a member remains",
);
check(await exists(...groupImage), "group image stays while a member remains");
check(await exists(...bAvatar), "remaining member's media stays");
check(
  (await sql(`select count(*) from auth.users where id='${a.aid}'`)) === "0",
  "auth user A removed",
);
check(
  (await sql(`
    select (select count(*) from public.users where id='${a.app}')
         + (select count(*) from public.wishlists where id='${wl}')
         + (select count(*) from public.messages where user_from='${a.app}')`)) ===
    "0",
  "A's rows cascade away",
);
check(
  (await sql(`select count(*) from public.families where id='${fam}'`)) === "1",
  "family remains for B",
);
check(
  (await sql(
    `select count(*) from public.messages where user_from='${b.app}'`,
  )) === "1",
  "B's messages remain",
);
check(
  (await sql(`
    select (select admin_id from public.families where id='${fam}') = '${b.app}'
       and (select user_from from public.conversations where id='${conv}') = '${b.app}'
       and (select owner_user_id from public.shopping_lists where id='${list}') = '${b.app}'`)) ===
    "t",
  "family admin, group chat and family list pass to the remaining member",
);
check(
  (await handleAccountDeletion(deletion(a.token), store)).status === 401,
  "a deleted account's token cannot be reused",
);

// Last member B leaves: the empty family and its shared media are removed.
check(
  (await handleAccountDeletion(deletion(b.token), store)).status === 204,
  "deletion of B",
);
check(!(await exists(...bAvatar)), "B's avatar removed");
check(
  !(await exists(...familyPhoto)),
  "family photo removed with the last member",
);
check(
  !(await exists(...groupImage)),
  "group image removed with the last member",
);
check(
  (await sql(`
    select (select count(*) from public.families where id='${fam}')
         + (select count(*) from public.conversations where id='${conv}')
         + (select count(*) from auth.users where id='${b.aid}')`)) === "0",
  "empty family, its chats and B are removed",
);

console.log(`Account deletion HTTP checks passed: ${checks}`);
