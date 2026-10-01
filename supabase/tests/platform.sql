-- Rollback-only recovery verification with fictional signup metadata.
begin;
do $$
declare
  profile public.users%rowtype;
begin
  if (select count(*) from pg_publication_tables
       where pubname='supabase_realtime' and schemaname='public') <> 14 then
    raise exception 'Recovered Realtime configuration is incomplete';
  end if;
  if (select count(*) from storage.buckets
       where id in ('avatars','group-images','wish-images','chat-media')) <> 4 then
    raise exception 'Recovered media buckets are incomplete';
  end if;
  if not exists (
    select 1 from pg_trigger t join pg_proc p on p.oid=t.tgfoid
    join pg_namespace n on n.oid=p.pronamespace
    where t.tgrelid='auth.users'::regclass and t.tgname='on_auth_user_created'
      and n.nspname='private' and p.proname='handle_new_auth_user'
  ) then raise exception 'Recovered signup trigger is missing or exposed'; end if;
  insert into auth.users(id,email,raw_user_meta_data) values
    ('00000000-0000-0000-0000-00000000b001','recovery@example.test',
     '{"full_name":"Recovery Fixture","phone":"fictional","avatar_color":2}');
  select * into strict profile from public.users
    where auth_id='00000000-0000-0000-0000-00000000b001';
  if profile.name <> 'Recovery Fixture' or profile.email <> 'recovery@example.test'
     or profile.mobile <> 'fictional' or profile.avatar_color <> 2 then
    raise exception 'Recovered signup did not preserve profile metadata';
  end if;
  if has_function_privilege('anon','private.handle_new_auth_user()','execute')
     or has_function_privilege('authenticated','private.handle_new_auth_user()','execute') then
    raise exception 'Recovered signup trigger function is callable by clients';
  end if;
end;
$$;
rollback;
