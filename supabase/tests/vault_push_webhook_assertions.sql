do $$ begin
 if (select decrypted_secret from vault.decrypted_secrets where name='family_push_webhook_key') <> repeat('x',120) then
   raise exception 'Credential did not transfer exactly'; end if;
 if (select tgfoid from pg_trigger where tgname='push-on-message' and tgrelid='public.messages'::regclass) <> 'private.notify_chat_message()'::regprocedure then
   raise exception 'Webhook still uses legacy trigger'; end if;
 if has_function_privilege('anon','private.notify_chat_message()','execute') or has_function_privilege('authenticated','private.notify_chat_message()','execute') then
   raise exception 'Webhook callable by clients'; end if;
 if (select pg_get_functiondef('private.notify_chat_message()'::regprocedure)) like '%xxxxxxxxxx%' then
   raise exception 'Credential embedded in function definition'; end if;
end; $$;
rollback;
