-- Recovery configuration after the public capture and security patches.
-- Existing production bucket visibility is deliberately preserved. New buckets
-- start private; signed-URL clients must be deployed before a new environment is used.
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function private.handle_new_auth_user();

insert into storage.buckets (id, name, public) values
  ('avatars', 'avatars', false),
  ('group-images', 'group-images', false),
  ('wish-images', 'wish-images', false),
  ('chat-media', 'chat-media', false)
on conflict (id) do nothing;

do $$
declare
  table_name text;
begin
  foreach table_name in array array[
    'birthdays', 'calendar_events', 'conversation_participants', 'conversations',
    'family_relations', 'meal_plans', 'message_reactions', 'messages',
    'shopping_items', 'shopping_lists', 'user_locations', 'wish_reservations',
    'wishes', 'wishlists'
  ] loop
    if not exists (
      select 1 from pg_publication_tables
       where pubname = 'supabase_realtime' and schemaname = 'public'
         and tablename = table_name
    ) then
      execute format('alter publication supabase_realtime add table public.%I', table_name);
    end if;
  end loop;
end;
$$;
