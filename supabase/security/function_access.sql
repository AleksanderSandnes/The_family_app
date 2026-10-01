-- Reviewed against the live 2026-09-30 schema. Run in a transaction after restoring
-- the baseline locally; production application waits for the reviewed rollout.
begin;
create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated, service_role;

alter function public.my_app_user_id() set schema private;
alter function public.my_family_id() set schema private;
alter function public.i_am_family_admin() set schema private;
alter function public.is_conversation_member(uuid, uuid) set schema private;
alter function public.join_family(text) set schema private;
alter function public.accept_wishlist_share(uuid) set schema private;
alter function public.ensure_wishlist_share_token(uuid) set schema private;
alter function public.ensure_conversation_participant(uuid) set schema private;
alter function public.handle_new_auth_user() set schema private;
alter function public.rls_auto_enable() set schema private;
alter function public.enforce_family_id_change() set schema private;
alter function public.enforce_family_member_update() set schema private;

alter function private.my_app_user_id() set search_path = pg_catalog;
alter function private.my_family_id() set search_path = pg_catalog;
alter function private.i_am_family_admin() set search_path = pg_catalog;
alter function private.join_family(text) set search_path = pg_catalog;
alter function private.accept_wishlist_share(uuid) set search_path = pg_catalog;
alter function private.ensure_wishlist_share_token(uuid) set search_path = pg_catalog;
alter function private.ensure_conversation_participant(uuid) set search_path = pg_catalog;
alter function private.handle_new_auth_user() set search_path = pg_catalog;
alter function private.enforce_family_id_change() set search_path = pg_catalog;
alter function private.enforce_family_member_update() set search_path = pg_catalog;

-- The membership helper must not disclose other users' conversation membership.
create or replace function private.is_conversation_member(conv_id uuid, uid uuid)
returns boolean language sql stable security definer set search_path = pg_catalog
as $$
  select uid = private.my_app_user_id() and exists (
    select 1 from public.conversation_participants
    where conversation_id = conv_id and user_id = uid
  );
$$;

-- Public invoker wrappers preserve client RPC names while privileged code lives
-- outside the Data API's exposed schemas. Existing policy dependencies retain OIDs.
create function public.my_app_user_id() returns uuid language sql stable security invoker
set search_path = pg_catalog as $$ select private.my_app_user_id(); $$;
create function public.my_family_id() returns uuid language sql stable security invoker
set search_path = pg_catalog as $$ select private.my_family_id(); $$;
create function public.i_am_family_admin() returns boolean language sql stable security invoker
set search_path = pg_catalog as $$ select private.i_am_family_admin(); $$;
create function public.is_conversation_member(conv_id uuid, uid uuid) returns boolean
language sql stable security invoker set search_path = pg_catalog
as $$ select private.is_conversation_member(conv_id, uid); $$;
create function public.join_family(p_code text) returns uuid language sql security invoker
set search_path = pg_catalog as $$ select private.join_family(p_code); $$;
create function public.accept_wishlist_share(p_token uuid) returns uuid language sql security invoker
set search_path = pg_catalog as $$ select private.accept_wishlist_share(p_token); $$;
create function public.ensure_wishlist_share_token(p_wishlist_id uuid) returns uuid
language sql security invoker set search_path = pg_catalog
as $$ select private.ensure_wishlist_share_token(p_wishlist_id); $$;
create function public.ensure_conversation_participant(conv_id uuid) returns void
language sql security invoker set search_path = pg_catalog
as $$ select private.ensure_conversation_participant(conv_id); $$;

revoke all on all functions in schema private from public, anon, authenticated;
grant execute on function private.my_app_user_id(), private.my_family_id(),
  private.i_am_family_admin(), private.is_conversation_member(uuid, uuid),
  private.join_family(text), private.accept_wishlist_share(uuid),
  private.ensure_wishlist_share_token(uuid), private.ensure_conversation_participant(uuid)
  to authenticated, service_role;

revoke all on all functions in schema public from public, anon;
grant execute on function public.my_app_user_id(), public.my_family_id(),
  public.i_am_family_admin(), public.is_conversation_member(uuid, uuid),
  public.join_family(text), public.accept_wishlist_share(uuid),
  public.ensure_wishlist_share_token(uuid), public.ensure_conversation_participant(uuid)
  to authenticated, service_role;
commit;
