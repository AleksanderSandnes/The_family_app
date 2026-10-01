-- Account deletion support for the delete-account Edge Function (Play/App Store
-- requirement). Deleting auth.users cascades to public.users and from there to
-- every user-owned row. Storage objects are not rows in those tables, so the
-- function asks for this plan first and removes the objects via the Storage API.
-- Apply after function_access.sql. Only service_role may execute these helpers.

-- Objects to remove when target_auth_id deletes their account:
--   * everything they uploaded to avatars, wish-images and chat-media;
--   * their own namespace in group-images;
--   * when they are the last member, the family photo and group-chat images
--     of their family, which nobody could reach afterwards.
create or replace function public.account_deletion_media(target_auth_id uuid)
returns table (bucket_id text, name text)
language sql stable security definer set search_path = pg_catalog
as $$
  with me as (
    select u.id, u.family_id from public.users u where u.auth_id = target_auth_id
  ), last_member as (
    select me.family_id from me
    where me.family_id is not null and not exists (
      select 1 from public.users o
      where o.family_id = me.family_id and o.id <> me.id
    )
  )
  select o.bucket_id, o.name from storage.objects o
  where (o.bucket_id in ('avatars', 'wish-images', 'chat-media')
         and o.owner_id = target_auth_id::text)
     or (o.bucket_id = 'group-images'
         and split_part(o.name, '/', 1) = target_auth_id::text)
     or (o.bucket_id = 'group-images' and exists (
           select 1 from last_member lm
           where split_part(o.name, '/', 1) = 'family-photos'
             and split_part(o.name, '/', 2) = lm.family_id::text
         ))
     or (o.bucket_id = 'group-images' and exists (
           select 1 from last_member lm
           join public.conversations c on c.family_id = lm.family_id
           where split_part(o.name, '/', 1) = c.id::text
         ));
$$;

-- The family a user belongs to, captured before deletion so an emptied family
-- can be removed afterwards.
create or replace function public.account_deletion_family(target_auth_id uuid)
returns uuid
language sql stable security definer set search_path = pg_catalog
as $$
  select u.family_id from public.users u where u.auth_id = target_auth_id;
$$;

-- Removes a family (and, through cascades, its shared lists, plans, chats and
-- wishlists) only when no member remains. Returns true when it was removed.
create or replace function public.purge_family_if_empty(target_family_id uuid)
returns boolean
language plpgsql security definer set search_path = pg_catalog
as $$
begin
  if target_family_id is null or exists (
    select 1 from public.users u where u.family_id = target_family_id
  ) then
    return false;
  end if;
  delete from public.families f where f.id = target_family_id;
  return found;
end;
$$;

-- Hands shared family containers to a remaining member before the cascade runs,
-- so other members keep group chats (and their own messages in them), the family
-- admin role and family shopping lists. Direct chats with the leaver are removed.
create or replace function public.prepare_account_deletion(target_auth_id uuid)
returns void
language plpgsql security definer set search_path = pg_catalog
as $$
declare
  leaver uuid;
  leaver_family uuid;
  heir uuid;
begin
  select u.id, u.family_id into leaver, leaver_family
  from public.users u where u.auth_id = target_auth_id;
  if leaver is null then
    return;
  end if;

  update public.conversations c set user_from = (
    select cp.user_id from public.conversation_participants cp
    where cp.conversation_id = c.id and cp.user_id <> leaver
    order by cp.joined_at, cp.user_id limit 1
  )
  where c.user_from = leaver and c.user_to is null and exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = c.id and cp.user_id <> leaver
  );

  if leaver_family is null then
    return;
  end if;
  select u.id into heir from public.users u
  where u.family_id = leaver_family and u.id <> leaver
  order by u.created_at, u.id limit 1;
  if heir is null then
    return;
  end if;
  update public.families f set admin_id = heir
  where f.id = leaver_family and f.admin_id = leaver;
  update public.shopping_lists s set owner_user_id = heir
  where s.owner_user_id = leaver and s.family_id = leaver_family;
end;
$$;

revoke all on function public.prepare_account_deletion(uuid) from public, anon, authenticated;
grant execute on function public.prepare_account_deletion(uuid) to service_role;
revoke all on function public.account_deletion_media(uuid) from public, anon, authenticated;
revoke all on function public.account_deletion_family(uuid) from public, anon, authenticated;
revoke all on function public.purge_family_if_empty(uuid) from public, anon, authenticated;
grant execute on function public.account_deletion_media(uuid) to service_role;
grant execute on function public.account_deletion_family(uuid) to service_role;
grant execute on function public.purge_family_if_empty(uuid) to service_role;
