-- Run only against an isolated local database after baseline + function_access.sql
-- + moderation.sql. Every fixture is fictional and the transaction is rolled back.
begin;
insert into auth.users(id) values
  ('00000000-0000-0000-0000-00000000a001'),
  ('00000000-0000-0000-0000-00000000a002'),
  ('00000000-0000-0000-0000-00000000a003');
insert into public.users(id, auth_id, name, email) values
  ('10000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-00000000a001', 'Emma Fixture', 'emma@example.test'),
  ('10000000-0000-0000-0000-00000000a002', '00000000-0000-0000-0000-00000000a002', 'Lars Fixture', 'lars@example.test'),
  ('10000000-0000-0000-0000-00000000a003', '00000000-0000-0000-0000-00000000a003', 'Nora Outsider', 'nora@example.test');
insert into public.families(id,name,join_code,admin_id) values
  ('20000000-0000-0000-0000-00000000a001','Fixture Nordmann','FIXTURE-MOD','10000000-0000-0000-0000-00000000a001');
update public.users set family_id='20000000-0000-0000-0000-00000000a001'
  where id in ('10000000-0000-0000-0000-00000000a001','10000000-0000-0000-0000-00000000a002');
insert into public.conversations(id,user_from,family_id) values
  ('30000000-0000-0000-0000-00000000a001','10000000-0000-0000-0000-00000000a001','20000000-0000-0000-0000-00000000a001');
insert into public.conversation_participants(conversation_id,user_id) values
  ('30000000-0000-0000-0000-00000000a001','10000000-0000-0000-0000-00000000a001'),
  ('30000000-0000-0000-0000-00000000a001','10000000-0000-0000-0000-00000000a002');
alter table public.messages disable trigger user;
insert into public.messages(id,conversation_id,user_from,text) values
  ('40000000-0000-0000-0000-00000000a001','30000000-0000-0000-0000-00000000a001','10000000-0000-0000-0000-00000000a002','Fictional rude message'),
  ('40000000-0000-0000-0000-00000000a002','30000000-0000-0000-0000-00000000a001','10000000-0000-0000-0000-00000000a001','Fictional own message');
alter table public.messages enable trigger user;

do $$
begin
  if has_function_privilege('anon','public.report_message(uuid,text,text)','execute') then
    raise exception 'Anonymous callers can report';
  end if;
  if has_table_privilege('anon','public.user_blocks','select') then
    raise exception 'Anonymous callers can read blocks';
  end if;
end;
$$;

set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-00000000a001',true);
do $$
declare
  rid uuid;
  r record;
begin
  rid := public.report_message('40000000-0000-0000-0000-00000000a001', 'harassment', '  fictional details  ');
  select * into r from public.content_reports where id = rid;
  if r.reported_user_id <> '10000000-0000-0000-0000-00000000a002'
     or r.message_snapshot <> 'Fictional rude message'
     or r.details <> 'fictional details'
     or r.status <> 'open' then
    raise exception 'Report was not captured server-side';
  end if;
  begin
    perform public.report_message('40000000-0000-0000-0000-00000000a002', 'spam', null);
    raise exception 'Own message was reportable';
  exception when invalid_parameter_value then null;
  end;
  begin
    perform public.report_message('40000000-0000-0000-0000-00000000a001', 'made-up', null);
    raise exception 'Unknown reason accepted';
  exception when check_violation then null;
  end;
  begin
    insert into public.content_reports(reporter_user_id, reason)
      values ('10000000-0000-0000-0000-00000000a001', 'spam');
    raise exception 'Direct report insert allowed';
  exception when insufficient_privilege then null;
  end;
  begin
    update public.content_reports set status = 'actioned' where id = rid;
    raise exception 'Reporter could change report status';
  exception when insufficient_privilege then null;
  end;

  insert into public.user_blocks(blocker_id, blocked_id)
    values ('10000000-0000-0000-0000-00000000a001', '10000000-0000-0000-0000-00000000a002');
  if (select count(*) from public.user_blocks) <> 1 then
    raise exception 'Own block not visible';
  end if;
  begin
    insert into public.user_blocks(blocker_id, blocked_id)
      values ('10000000-0000-0000-0000-00000000a002', '10000000-0000-0000-0000-00000000a001');
    raise exception 'Blocked on behalf of another user';
  exception when insufficient_privilege then null;
  end;
end;
$$;

-- The blocked user cannot see who blocked them or read the reports against them.
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-00000000a002',true);
do $$
begin
  if (select count(*) from public.user_blocks) <> 0 then
    raise exception 'Blocked user can see the block';
  end if;
  if (select count(*) from public.content_reports) <> 0 then
    raise exception 'Reported user can read reports';
  end if;
end;
$$;

-- A non-participant cannot report (or probe) messages in another conversation.
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-00000000a003',true);
do $$
begin
  perform public.report_message('40000000-0000-0000-0000-00000000a001', 'spam', null);
  raise exception 'Outsider could report a foreign message';
exception when no_data_found then null;
end;
$$;

-- Unblocking removes the row for the blocker.
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-00000000a001',true);
do $$
begin
  delete from public.user_blocks where blocked_id = '10000000-0000-0000-0000-00000000a002';
  if (select count(*) from public.user_blocks) <> 0 then
    raise exception 'Unblock failed';
  end if;
end;
$$;
rollback;
