# Private media rollout (staged; no live changes applied)

Apply `function_access.sql`, then `storage_writes.sql`, then `storage_reads.sql`
only after testing a restored backend. Read guards preserve existing permissive
policies while imposing ownership, family, conversation or explicit wishlist-share
checks. Anonymous access to all four buckets is denied by object RLS. Public
bucket downloads still bypass RLS until the visibility change below.

Wish sharing authorizes the exact image referenced by an accessible wishlist,
not every image in its owner's folder. The reference must use the existing
`bntcznvsbyshetndbxfa.supabase.co` public locator, and its uploader must belong to
that wishlist's owner/family. This prevents a forged user ID or image URL from
lending access to another family's storage. Current uploaded wish filenames are
UUID/timestamp ASCII paths. Before supporting a custom storage origin or encoded
wish filenames, update this exact comparison and add isolation fixtures; unfamiliar
references fail closed. Stable database URLs must remain stable, never signed.

Local rollback-only tests cover anonymous, unregistered and unrelated users,
family and conversation membership, explicit shares, forged foreign references,
unsafe paths and immediate share/member revocation. Write tests run both before
and after read guards. Local security advisors report no issues; this does not
represent a new production audit. Database tests do not prove Storage HTTP behavior.

Before changing bucket visibility:

1. Require Android and iOS client CI to pass, then test uploads/upserts, avatars,
   family/group images, shared wishlists/PDFs, gallery saves and voice notes against
   an isolated full Storage stack using fictional users in two families.
2. Confirm both signed-URL creation and authenticated download reject unrelated
   and anonymous callers; verify signed downloads work and expired links fail.
3. Release clients with `FamilyMedia` and establish a minimum supported version.
   Old clients read public URLs directly and will lose media after the switch.
4. Deploy read/write guards and confirm advisors and cross-account smoke checks.
5. In a coordinated rollout, set `public = false` for exactly `avatars`,
   `group-images`, `wish-images` and `chat-media`. Validate all supported clients.

Neither this document nor the policy scripts changes production bucket flags.
Signed URLs remain usable until their five-minute expiry, including after a share
or session is revoked. Clients reject account changes during signing/download and
bypass protected image caches; already displayed/exported media cannot be recalled.
Legacy cache entries are bypassed but have not been erased. Full signed-release
upgrade/sign-out checks and migration bootstrap reproduction remain open.
