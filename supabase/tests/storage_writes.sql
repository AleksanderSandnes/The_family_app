-- Fictional local fixtures only. Legacy permissive policies remain installed to
-- prove the new restrictive guards cannot be bypassed by their OR combination.
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

set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
do $$
declare
  path text;
  bucket text;
  affected integer;
begin
  for bucket,path in select * from (values
    ('avatars','00000000-0000-0000-0000-000000000001/avatar.jpg'),
    ('wish-images','10000000-0000-0000-0000-000000000001/wish.jpg'),
    ('group-images','family-photos/20000000-0000-0000-0000-000000000001/photo.jpg'),
    ('group-images','30000000-0000-0000-0000-000000000001/image.jpg'),
    ('chat-media','30000000-0000-0000-0000-000000000001/00000000-0000-0000-0000-000000000001/image.jpg')
  ) as allowed(bucket,path) loop
    insert into storage.objects(bucket_id,name) values (bucket,path);
  end loop;

  for bucket,path in select * from (values
    ('avatars','00000000-0000-0000-0000-000000000002/new.jpg'),
    ('wish-images','10000000-0000-0000-0000-000000000002/new.jpg'),
    ('group-images','family-photos/20000000-0000-0000-0000-000000000002/new.jpg'),
    ('group-images','30000000-0000-0000-0000-000000000002/new.jpg'),
    ('chat-media','30000000-0000-0000-0000-000000000002/00000000-0000-0000-0000-000000000001/new.jpg'),
    ('chat-media','30000000-0000-0000-0000-000000000001/00000000-0000-0000-0000-000000000002/new.jpg'),
    ('wish-images','10000000-0000-0000-0000-000000000001/../new.jpg')
  ) as forbidden(bucket,path) loop
    begin
      insert into storage.objects(bucket_id,name) values (bucket,path);
      raise exception 'Unauthorized insert succeeded: %',bucket;
    exception when insufficient_privilege then null;
    end;
  end loop;

  update storage.objects set metadata='{"changed":true}'::jsonb
    where bucket_id='chat-media' and name='30000000-0000-0000-0000-000000000001/00000000-0000-0000-0000-000000000001/image.jpg';
  get diagnostics affected = row_count;
  if affected <> 1 then raise exception 'Own chat media replacement denied'; end if;

  update storage.objects set metadata='{"changed":true}'::jsonb
    where name in ('10000000-0000-0000-0000-000000000002/bob.jpg',
      'family-photos/20000000-0000-0000-0000-000000000002/photo.jpg',
      '30000000-0000-0000-0000-000000000002/image.jpg');
  get diagnostics affected = row_count;
  if affected <> 0 then raise exception 'Other user media was overwritten'; end if;
  delete from storage.objects where name like '%000000000002%';
  get diagnostics affected = row_count;
  if affected <> 0 then raise exception 'Other user media was deleted'; end if;
  begin
    update storage.objects set name='10000000-0000-0000-0000-000000000002/moved.jpg'
      where bucket_id='wish-images' and name='10000000-0000-0000-0000-000000000001/wish.jpg';
    raise exception 'Own media moved into another user namespace';
  exception when insufficient_privilege then null;
  end;
  update storage.objects set metadata='{"changed":true}'::jsonb
    where bucket_id='wish-images' and name='10000000-0000-0000-0000-000000000001/wish.jpg';
  get diagnostics affected = row_count;
  if affected <> 1 then raise exception 'Own image update denied'; end if;
  delete from storage.objects where bucket_id='wish-images' and name='10000000-0000-0000-0000-000000000001/wish.jpg';
  get diagnostics affected = row_count;
  if affected <> 1 then raise exception 'Own image delete denied'; end if;
end;
$$;
rollback;
