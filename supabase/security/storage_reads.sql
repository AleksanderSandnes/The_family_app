-- Install after function_access.sql and storage_writes.sql. This is staged SQL;
-- it does NOT change bucket visibility. Public downloads still bypass object RLS.
begin;
-- Privileged lookup is needed for shared wishlists/cross-family conversations:
-- public.users RLS hides their owners. Every branch checks the caller's actual
-- app identity/membership; user-editable JWT metadata is never authorization.
create or replace function private.can_read_media(media_bucket text, object_name text)
returns boolean language sql stable security definer set search_path = pg_catalog
as $$
  select auth.uid() is not null
    and private.my_app_user_id() is not null
    and object_name !~ '(^|/)(\.{1,2})(/|$)'
    and object_name !~ '[%\\[:cntrl:]]'
    and object_name not like '%//%' and object_name not like '/%'
    and object_name not like '%/'
    and array_length(string_to_array(object_name, '/'), 1) >=
      case when media_bucket = 'chat-media' or object_name like 'family-photos/%' then 3 else 2 end
    and (
      private.can_manage_media(media_bucket, object_name) or
      case media_bucket
        when 'avatars' then exists (
          select 1 from public.users owner
          where owner.auth_id::text = split_part(object_name, '/', 1)
            and owner.deleted_at is null
            and (
              owner.family_id = private.my_family_id() or exists (
                select 1 from public.conversation_participants theirs
                join public.conversation_participants mine using (conversation_id)
                where theirs.user_id = owner.id and mine.user_id = private.my_app_user_id()
              ) or exists (
                select 1 from public.wishlists wl
                join public.wishlist_shares share on share.wishlist_id = wl.id
                where wl.owner_user_id = owner.id and share.user_id = private.my_app_user_id()
                  and wl.deleted_at is null
              )
            )
        )
        when 'wish-images' then exists (
          select 1 from public.wishes w
          join public.wishlists wl on wl.id = w.wishlist_id
          join public.users uploader on uploader.id = w.user_id
          where split_part(object_name, '/', 1) in (uploader.id::text, uploader.auth_id::text)
            and split_part(w.image_url, '?', 1) =
              'https://bntcznvsbyshetndbxfa.supabase.co/storage/v1/object/public/wish-images/' || object_name
            -- wishes.user_id is editable under legacy family policies. A forged
            -- reference must not lend access to an unrelated uploader namespace.
            and (uploader.id = wl.owner_user_id or uploader.family_id = wl.family_id)
            and w.deleted_at is null and wl.deleted_at is null
            and (wl.owner_user_id = private.my_app_user_id()
              or wl.family_id = private.my_family_id()
              or exists (select 1 from public.wishlist_shares share
                where share.wishlist_id = wl.id and share.user_id = private.my_app_user_id()))
        )
        when 'chat-media' then exists (
          select 1 from public.conversation_participants cp
          where cp.conversation_id::text = split_part(object_name, '/', 1)
            and cp.user_id = private.my_app_user_id()
        )
        when 'group-images' then exists (
          select 1 from public.users owner
          where owner.auth_id::text = split_part(object_name, '/', 1)
            and owner.deleted_at is null and owner.family_id = private.my_family_id()
        )
        else false
      end
    );
$$;
revoke all on function private.can_read_media(text, text) from public, anon;
grant execute on function private.can_read_media(text, text) to authenticated, service_role;

-- Separate anonymous/authenticated guards avoid executing a privileged lookup
-- under anon. Other buckets retain their own policies. Existing permissive reads
-- stay present so tests prove they cannot OR their way around these restrictions.
drop policy if exists media_read_scope_anon on storage.objects;
drop policy if exists media_read_scope on storage.objects;
create policy media_read_scope_anon on storage.objects as restrictive
  for select to anon using (
    bucket_id not in ('avatars','wish-images','chat-media','group-images')
  );
create policy media_read_scope on storage.objects as restrictive
  for select to authenticated using (
    bucket_id not in ('avatars','wish-images','chat-media','group-images')
    or private.can_read_media(bucket_id, name)
  );
commit;
