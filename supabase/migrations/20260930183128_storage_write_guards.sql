-- First stage: restrict writes while existing clients still resolve public URLs.
-- Private reads require a coordinated client release and are NOT solved by this file.
-- Apply after function_access.sql; validate locally before production rollout.
create or replace function private.can_manage_media(media_bucket text, object_name text)
returns boolean language sql stable security definer set search_path = pg_catalog
as $$
  select auth.uid() is not null and case media_bucket
    when 'avatars' then split_part(object_name, '/', 1) = auth.uid()::text
    when 'wish-images' then
      split_part(object_name, '/', 1) in
        (auth.uid()::text, private.my_app_user_id()::text)
    when 'chat-media' then
      split_part(object_name, '/', 2) = auth.uid()::text and exists (
        select 1 from public.conversation_participants cp
        where cp.conversation_id::text = split_part(object_name, '/', 1)
          and cp.user_id = private.my_app_user_id()
      )
    when 'group-images' then
      case when split_part(object_name, '/', 1) = 'family-photos' then
        split_part(object_name, '/', 2) = private.my_family_id()::text
      else
        split_part(object_name, '/', 1) = auth.uid()::text or exists (
          select 1 from public.conversation_participants cp
          where cp.conversation_id::text = split_part(object_name, '/', 1)
            and cp.user_id = private.my_app_user_id()
        )
      end
    else false
  end and object_name not like '%//%' and object_name not like '/%'
    and object_name not like '%/../%' and object_name not like '%/./%'
    and array_length(string_to_array(object_name, '/'), 1) >=
      case when media_bucket = 'chat-media' or object_name like 'family-photos/%' then 3 else 2 end
    and split_part(reverse(object_name), '/', 1) <> '';
$$;
revoke all on function private.can_manage_media(text, text) from public, anon;
grant execute on function private.can_manage_media(text, text) to authenticated, service_role;

-- Chat uploads use upsert on iOS. The legacy bucket has INSERT/SELECT/DELETE
-- but no permissive UPDATE policy, so replacing an owned object fails even when
-- all restrictive guards pass. Authorize only the member's own upload namespace.
drop policy if exists chat_media_owner_update on storage.objects;
create policy chat_media_owner_update on storage.objects for update to authenticated
  using (bucket_id = 'chat-media' and private.can_manage_media(bucket_id, name))
  with check (bucket_id = 'chat-media' and private.can_manage_media(bucket_id, name));

-- Restrictive guards AND with every existing permissive write policy, preventing
-- an old broad policy from re-opening another family's files. Preserve other buckets.
drop policy if exists media_write_scope_insert on storage.objects;
drop policy if exists media_write_scope_update on storage.objects;
drop policy if exists media_write_scope_delete on storage.objects;
create policy media_write_scope_insert on storage.objects as restrictive
  for insert to authenticated with check (
    bucket_id not in ('avatars','wish-images','chat-media','group-images')
    or private.can_manage_media(bucket_id, name)
  );
create policy media_write_scope_update on storage.objects as restrictive
  for update to authenticated using (
    bucket_id not in ('avatars','wish-images','chat-media','group-images')
    or private.can_manage_media(bucket_id, name)
  ) with check (
    bucket_id not in ('avatars','wish-images','chat-media','group-images')
    or private.can_manage_media(bucket_id, name)
  );
create policy media_write_scope_delete on storage.objects as restrictive
  for delete to authenticated using (
    bucket_id not in ('avatars','wish-images','chat-media','group-images')
    or private.can_manage_media(bucket_id, name)
  );
