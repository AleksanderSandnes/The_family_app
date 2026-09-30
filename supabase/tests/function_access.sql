-- Run only against an isolated local database after baseline + function_access.sql.
-- Every fixture is fictional and the transaction is rolled back.
begin;
insert into auth.users(id) values
  ('00000000-0000-0000-0000-000000000001'),
  ('00000000-0000-0000-0000-000000000002');
insert into public.users(id, auth_id, name, email) values
  ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'Alice Fixture', 'alice@example.test'),
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'Bob Fixture', 'bob@example.test');
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

do $$
begin
  if exists (
    select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname='public' and p.prosecdef
  ) then raise exception 'Privileged code remains in exposed schema'; end if;
  if has_function_privilege('anon','public.join_family(text)','execute') then
    raise exception 'Anonymous callers can join a family';
  end if;
end;
$$;

set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
do $$
begin
  if public.my_app_user_id() <> '10000000-0000-0000-0000-000000000001'::uuid then
    raise exception 'Identity wrapper failed';
  end if;
  if (select count(*) from public.conversations) <> 1 then
    raise exception 'Conversation isolation failed';
  end if;
  if public.is_conversation_member('30000000-0000-0000-0000-000000000002',
                                  '10000000-0000-0000-0000-000000000002') then
    raise exception 'Membership helper disclosed another user';
  end if;
  begin
    update public.users set family_id='20000000-0000-0000-0000-000000000002'
      where id='10000000-0000-0000-0000-000000000001';
    raise exception 'Direct family reassignment unexpectedly succeeded';
  exception when insufficient_privilege then null;
  end;
  if public.join_family('FIXTURE-TWO') <> '20000000-0000-0000-0000-000000000002'::uuid then
    raise exception 'Authenticated join RPC failed';
  end if;
  if public.my_family_id() <> '20000000-0000-0000-0000-000000000002'::uuid then
    raise exception 'Join RPC did not update family membership';
  end if;
end;
$$;
rollback;
