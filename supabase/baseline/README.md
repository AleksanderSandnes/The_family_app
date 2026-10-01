# Database recovery evidence

`20260930_public.sql` is a schema-only capture of the live public schema before
the September security migrations. It contains no application rows. Its embedded
service-role webhook credential was removed; notification delivery needs separate
secure setup.

## Empty-database recovery

Run against an **empty, isolated Supabase database**:

```sh
psql -v ON_ERROR_STOP=1 -f supabase/baseline/restore.sql
```

The entry point restores the captured schema, applies the function/storage,
account-deletion and moderation security patches, then installs `platform.sql`:

- signup profile creation through the private Auth trigger;
- the 14 public tables in the live Realtime publication;
- four media buckets, private when newly created.

The entry point rejects an existing application schema. Security stages commit
independently: discard the disposable database if a stage fails. Do not use this
as a production upgrade. `platform.sql` can be reapplied and preserves existing
bucket visibility; production media stays public until signed-URL clients and
minimum-version enforcement are released.

CI restores an empty database, verifies the private trigger and publication,
checks profile creation via real local Auth, and rejects a second restore. The
existing security job separately verifies isolation, deletion and Vault transfer.
The local recovery checks use fictional fixtures and clean up their Auth user.

## Remaining reconciliation

The 34 original journal files begin by altering pre-existing tables; six later
security migrations do not fill that historical gap. `supabase db reset` using
that journal alone still cannot create a complete application database. This
explicit recovery path does not rewrite the production migration journal or
claim ordered-migration reproducibility.

`../post_deploy/push_webhook.sql` reads `family_push_webhook_key` from Vault.
Provision it securely before installing notification wiring. Recovery does not
install an outbound push webhook or scheduled daily-reminder job. Cron/Vault
configuration, credentials, endpoint reconciliation and production key rotation
remain release work. Never put server credentials in this capture or trigger text.
