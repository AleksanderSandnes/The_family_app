# Database recovery evidence

`20260930_public.sql` is a schema-only capture of the live public schema. It
contains no application rows. Its embedded service-role webhook credential was
removed; install webhook wiring separately after configuring Vault.

The 34 files in `../migrations/` were fetched from the remote migration journal.
They preserve the recorded history, but begin with alterations to tables created
outside that journal. They are **not a complete fresh-install sequence**.
Do not run them against a fresh database or claim migration reproducibility yet.

The captured public schema restores successfully into a fresh local Supabase
database. CI tests that restore, then applies `../security/function_access.sql`
and runs rollback-only fictional fixtures. Storage policies, auth triggers,
Realtime publication configuration, scheduled jobs and baseline reconciliation
still need to be captured and verified before adopting a production migration
pipeline. No remote migration journal has been rewritten.

`../post_deploy/push_webhook.sql` reads `family_push_webhook_key` from Vault.
Provision that credential securely before applying the script. Deploy the
notification authorization changes together with the reviewed webhook setup.
Production key rotation and rollout remain pending review.
