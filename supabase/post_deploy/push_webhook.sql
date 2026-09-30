-- Apply only after provisioning family_push_webhook_key in Supabase Vault.
-- Never paste the key into a trigger definition or commit it to SQL.
begin;

do $$
begin
  if not exists (select 1 from vault.secrets where name = 'family_push_webhook_key') then
    raise exception 'Provision family_push_webhook_key in Vault before installing the webhook';
  end if;
end;
$$;

create schema if not exists private;
revoke all on schema private from public, anon;

create or replace function private.notify_chat_message()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog
as $$
declare
  webhook_key text;
begin
  select decrypted_secret into webhook_key
    from vault.decrypted_secrets where name = 'family_push_webhook_key';
  if webhook_key is null then
    raise exception 'Push webhook credential is unavailable';
  end if;
  perform net.http_post(
    url := 'https://bntcznvsbyshetndbxfa.supabase.co/functions/v1/push-on-message',
    headers := jsonb_build_object('Content-Type', 'application/json',
                                 'Authorization', 'Bearer ' || webhook_key),
    body := jsonb_build_object('type', TG_OP, 'table', TG_TABLE_NAME,
                              'schema', TG_TABLE_SCHEMA, 'record', to_jsonb(NEW),
                              'old_record', null),
    timeout_milliseconds := 5000
  );
  return NEW;
end;
$$;

revoke all on function private.notify_chat_message() from public, anon, authenticated;
drop trigger if exists "push-on-message" on public.messages;
create trigger "push-on-message" after insert on public.messages
  for each row execute function private.notify_chat_message();
commit;
