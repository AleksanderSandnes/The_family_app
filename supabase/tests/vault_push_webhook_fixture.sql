-- Disposable local fixture; rolled back by the assertions script.
begin;
delete from vault.secrets where name='family_push_webhook_key';
drop trigger if exists "push-on-message" on public.messages;
do $$ begin
 execute format('create trigger "push-on-message" after insert on public.messages for each row execute function supabase_functions.http_request(%L,%L,%L,%L,%L)',
 'http://127.0.0.1:58321/functions/v1/push-on-message','POST',
 jsonb_build_object('Content-Type','application/json','Authorization','Bearer '||repeat('x',120))::text,'{}','5000');
end; $$;
