-- Rollback-only fictional fixtures. Legacy broad policies intentionally remain.
begin;
-- Simulate the Storage API's deletion permission in this rollback-only database test.
set local storage.allow_delete_query = 'true';
insert into auth.users(id) values
  ('00000000-0000-0000-0000-000000000001'),
  ('00000000-0000-0000-0000-000000000002');
insert into public.users(id,auth_id,name,email) values
  ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','Alice Fixture','alice@example.test'),
  ('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000002','Bob Fixture','bob@example.test');
insert into public.families(id,name,join_code,admin_id) values
  ('20000000-0000-0000-0000-000000000001','Fixture One','FIXTURE-ONE','10000000-0000-0000-0000-000000000001'),
  ('20000000-0000-0000-0000-000000000002','Fixture Two','FIXTURE-TWO','10000000-0000-0000-0000-000000000002');
update public.users set family_id='20000000-0000-0000-0000-000000000001' where name='Alice Fixture';
update public.users set family_id='20000000-0000-0000-0000-000000000002' where name='Bob Fixture';
insert into public.conversations(id,user_from,family_id) values
  ('30000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001'),
  ('30000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000002');
insert into public.conversation_participants(conversation_id,user_id) values
  ('30000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001'),
  ('30000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000002');
insert into storage.objects(bucket_id,name) values
  ('wish-images','10000000-0000-0000-0000-000000000002/bob.jpg'),
  ('group-images','family-photos/20000000-0000-0000-0000-000000000002/photo.jpg'),
  ('group-images','30000000-0000-0000-0000-000000000002/image.jpg');

insert into auth.users(id) values ('00000000-0000-0000-0000-000000000003');
insert into public.users(id,auth_id,name,email,family_id) values
  ('10000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000003',
   'Carol Fixture','carol@example.test','20000000-0000-0000-0000-000000000001');
insert into public.wishlists(id,owner_user_id,family_id,name) values
  ('40000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000002','Shared'),
  ('40000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000002','Unshared'),
  ('40000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000001','Family');
insert into public.wishes(wishlist_id,user_id,text,image_url) values
  ('40000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002','Shared image',
   'https://bntcznvsbyshetndbxfa.supabase.co/storage/v1/object/public/wish-images/10000000-0000-0000-0000-000000000002/shared.jpg?t=123'),
  ('40000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000002','Unshared image',
   'https://bntcznvsbyshetndbxfa.supabase.co/storage/v1/object/public/wish-images/10000000-0000-0000-0000-000000000002/hidden.jpg'),
  ('40000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000003','Family image',
   'https://bntcznvsbyshetndbxfa.supabase.co/storage/v1/object/public/wish-images/10000000-0000-0000-0000-000000000003/family.jpg'),
  ('40000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000002','Forged foreign locator',
   'https://bntcznvsbyshetndbxfa.supabase.co/storage/v1/object/public/wish-images/10000000-0000-0000-0000-000000000002/hidden.jpg');
insert into public.wishlist_shares(wishlist_id,user_id) values
  ('40000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001');
insert into public.conversation_participants(conversation_id,user_id) values
  ('30000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002');
insert into storage.objects(bucket_id,name) values
  ('avatars','00000000-0000-0000-0000-000000000001/own.jpg'),
  ('avatars','00000000-0000-0000-0000-000000000002/foreign.jpg'),
  ('avatars','00000000-0000-0000-0000-000000000003/family.jpg'),
  ('wish-images','10000000-0000-0000-0000-000000000001/own.jpg'),
  ('wish-images','10000000-0000-0000-0000-000000000002/shared.jpg'),
  ('wish-images','10000000-0000-0000-0000-000000000002/hidden.jpg'),
  ('wish-images','10000000-0000-0000-0000-000000000003/family.jpg'),
  ('group-images','family-photos/20000000-0000-0000-0000-000000000001/own.jpg'),
  ('group-images','30000000-0000-0000-0000-000000000001/own.jpg'),
  ('group-images','00000000-0000-0000-0000-000000000003/family.jpg'),
  ('chat-media','30000000-0000-0000-0000-000000000001/00000000-0000-0000-0000-000000000002/own.jpg'),
  ('chat-media','30000000-0000-0000-0000-000000000002/00000000-0000-0000-0000-000000000002/foreign.jpg'),
  ('avatars','00000000-0000-0000-0000-000000000001/../unsafe.jpg');

set local role anon;
select set_config('request.jwt.claim.sub','',true);
do $$ begin
  if exists(select 1 from storage.objects) then raise exception 'Anonymous media listing/read allowed'; end if;
end $$;

set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
do $$
declare actual integer;
begin
  select count(*) into actual from storage.objects;
  if actual <> 10 then raise exception 'Expected 10 authorized objects, got %', actual; end if;
  if exists(select 1 from storage.objects where name like '%hidden.jpg' or name like '%bob.jpg'
    or name like '%unsafe.jpg' or (bucket_id = 'chat-media' and name like '%foreign.jpg')) then
    raise exception 'Foreign/unshared/unsafe media readable';
  end if;
  if not exists(select 1 from storage.objects where name like '%shared.jpg') then
    raise exception 'Explicitly shared wish image denied';
  end if;
end $$;

-- Revocation takes effect in the same session, without waiting for JWT refresh.
reset role;
delete from public.wishlist_shares where wishlist_id='40000000-0000-0000-0000-000000000001';
set local role authenticated;
do $$ begin
  if exists(select 1 from storage.objects where name like '%shared.jpg') then
    raise exception 'Revoked wishlist share still readable';
  end if;
  if not exists(select 1 from storage.objects where bucket_id='avatars' and name like '%foreign.jpg') then
    raise exception 'Cross-family conversation avatar denied';
  end if;
end $$;
reset role;
delete from public.conversation_participants where user_id='10000000-0000-0000-0000-000000000001';
set local role authenticated;
do $$ begin
  if exists(select 1 from storage.objects where name like '%shared.jpg' or name like '%foreign.jpg'
    or bucket_id='chat-media' or (bucket_id='group-images' and name like '3000%')) then
    raise exception 'Revoked share/conversation still readable';
  end if;
end $$;

select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000004',true);
do $$ begin
  if exists(select 1 from storage.objects) then raise exception 'Unregistered account media allowed'; end if;
end $$;
rollback;
