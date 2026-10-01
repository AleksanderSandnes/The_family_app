-- Fictional store/review demo family for DISPOSABLE databases only (local stack restored
-- from supabase/baseline/restore.sql, or a Supabase preview branch).
--
-- Everyone here is invented: the "Nordmann" family, @example.com addresses (RFC 2606
-- reserved domain), chat messages, meals, wishes and events. Map pins sit on public
-- landmarks in Oslo, never a home. The guard refuses any database that already holds a
-- non-example.com account, so it cannot touch production by accident. The production
-- reviewer account is created separately with a secret password (see store/README.md).
--
-- Login: demo@example.com / store-demo-local-only (local stack only).

do $$
begin
  if exists (select 1 from auth.users where email not like '%@example.com') then
    raise exception 'seed_demo.sql only runs on disposable databases (real accounts found)';
  end if;
end;
$$;

create or replace function pg_temp.demo_auth_user(
  auth_id uuid, email text, full_name text, birthday text, argb text
) returns uuid language plpgsql as $$
declare
  profile_id uuid;
begin
  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, email_change, email_change_token_new, recovery_token
  ) values (
    '00000000-0000-0000-0000-000000000000', auth_id, 'authenticated', 'authenticated', email,
    extensions.crypt('store-demo-local-only', extensions.gen_salt('bf')), now(),
    '{"provider":"email","providers":["email"]}',
    jsonb_build_object('full_name', full_name, 'birthday', birthday,
                       'avatar_color', ('x' || argb)::bit(32)::int),
    now(), now(), '', '', '', ''
  ) on conflict (id) do nothing;

  insert into auth.identities (
    id, user_id, provider_id, identity_data, provider, last_sign_in_at, created_at, updated_at
  ) values (
    auth_id, auth_id, auth_id::text,
    jsonb_build_object('sub', auth_id::text, 'email', email, 'email_verified', true),
    'email', now(), now(), now()
  ) on conflict do nothing;

  select id into profile_id from public.users where users.auth_id = demo_auth_user.auth_id;
  return profile_id;
end;
$$;

do $$
declare
  emma uuid := pg_temp.demo_auth_user('00000000-0000-4000-8000-0000000fa001',
    'demo@example.com', 'Emma Nordmann', '1988-04-12', 'FF6366F1');
  lars uuid := pg_temp.demo_auth_user('00000000-0000-4000-8000-0000000fa002',
    'lars@example.com', 'Lars Nordmann', '1986-09-03', 'FF14B8A6');
  nora uuid := pg_temp.demo_auth_user('00000000-0000-4000-8000-0000000fa003',
    'nora@example.com', 'Nora Nordmann', '2014-06-21', 'FFEC4899');
  jonas uuid := pg_temp.demo_auth_user('00000000-0000-4000-8000-0000000fa004',
    'jonas@example.com', 'Jonas Nordmann', '2017-11-30', 'FFF59E0B');
  family uuid := '00000000-0000-4000-8000-0000000fb001';
  chat uuid := '00000000-0000-4000-8000-0000000fc001';
  groceries uuid := '00000000-0000-4000-8000-0000000fd001';
  meals uuid := '00000000-0000-4000-8000-0000000fe001';
  wishlist uuid := '00000000-0000-4000-8000-0000000ff001';
  monday date := date_trunc('week', current_date)::date;
  member uuid;
begin
  insert into public.families (id, name, join_code, admin_id)
  values (family, 'Family Nordmann', 'DEMO42', emma)
  on conflict (id) do nothing;
  update public.users
     set family_id = family, last_active_at = now(), mobile = '+47 400 00 000'
   where id in (emma, lars, nora, jonas);

  insert into public.family_relations (family_id, from_user_id, to_user_id, relation) values
    (family, emma, lars, 'Husband'), (family, emma, nora, 'Daughter'),
    (family, emma, jonas, 'Son');

  -- Family group chat.
  insert into public.conversations (id, user_from, name, family_id)
  values (chat, emma, 'Family Nordmann', family) on conflict (id) do nothing;
  foreach member in array array[emma, lars, nora, jonas] loop
    insert into public.conversation_participants (conversation_id, user_id, last_read_at)
    values (chat, member, now());
  end loop;
  insert into public.messages (conversation_id, user_from, text, sent_at) values
    (chat, lars, 'Picking up groceries on the way home. Anything missing?', now() - interval '95 minutes'),
    (chat, emma, 'Oat milk and tomatoes, please!', now() - interval '92 minutes'),
    (chat, nora, 'Can we have tacos on Friday? 🌮', now() - interval '60 minutes'),
    (chat, jonas, 'Yes!! Tacos!', now() - interval '58 minutes'),
    (chat, emma, 'Tacos it is. Football practice moved to 17:30 tomorrow ⚽', now() - interval '20 minutes');

  -- Shopping.
  insert into public.shopping_lists (id, title, owner_user_id, family_id, icon, color)
  values (groceries, 'Groceries', emma, family, 'shopping_cart', ('x' || 'FF14B8A6')::bit(32)::int)
  on conflict (id) do nothing;
  insert into public.shopping_items (list_id, item, checked) values
    (groceries, 'Oat milk', false), (groceries, 'Tomatoes', false),
    (groceries, 'Taco shells', false), (groceries, 'Avocados', false),
    (groceries, 'Cheddar', true), (groceries, 'Apples', true);

  -- Meals for the current week.
  insert into public.meal_plans (id, family_id, from_date, to_date, week, name, icon, created_by)
  values (meals, family, monday::text, (monday + 6)::text,
          extract(week from monday)::int, 'This week', 'restaurant', emma)
  on conflict (id) do nothing;
  insert into public.meal_plan_days (meal_plan_id, day, date, food)
  select meals, to_char(monday + i, 'FMDay'), (monday + i)::text,
         (array['Salmon with potatoes', 'Vegetable soup', 'Pasta bolognese', 'Fish cakes',
                'Tacos', 'Homemade pizza', 'Roast chicken'])[i + 1]
    from generate_series(0, 6) as i;

  -- Calendar.
  insert into public.calendar_events
    (user_id, family_id, date_from, date_to, time_from, time_to, activity, all_day, icon, attendee_ids)
  values
    (jonas, family, current_date::text, current_date::text, '15:00', '15:30',
     'School pickup', false, 'school', array[emma::text]),
    (nora, family, (current_date + 1)::text, (current_date + 1)::text, '17:30', '19:00',
     'Football practice', false, 'fitness_center', array[lars::text]),
    (emma, family, (current_date + 2)::text, (current_date + 2)::text, '18:00', '19:00',
     'Parent-teacher meeting', false, 'school', array[lars::text]),
    (jonas, family, (current_date + 4)::text, (current_date + 4)::text, '10:00', '12:00',
     'Swimming lesson', false, 'star', array[emma::text]),
    (lars, family, (current_date + 9)::text, (current_date + 11)::text, '', '',
     'Cabin weekend', true, 'home', array[emma::text, nora::text, jonas::text]);

  -- Birthdays (members + a fictional grandparent).
  insert into public.birthdays (name, date, family_id, user_id, made_by_user_id, icon) values
    ('Nora Nordmann', '2014-06-21', family, nora, emma, 'cake'),
    ('Jonas Nordmann', '2017-11-30', family, jonas, emma, 'cake'),
    ('Grandma Ingrid', to_char(current_date + 12, 'YYYY') || '-' || to_char(current_date + 12, 'MM-DD'),
     family, null, emma, 'celebration');

  -- Wishlist.
  insert into public.wishlists (id, owner_user_id, name, family_id, icon)
  values (wishlist, nora, 'Nora''s birthday', family, 'card_giftcard')
  on conflict (id) do nothing;
  insert into public.wishes (wishlist_id, user_id, text, price, description, checked) values
    (wishlist, nora, 'Football boots', '699 kr', 'Size 36, any colour', false),
    (wishlist, nora, 'Drawing tablet', '1 290 kr', null, false),
    (wishlist, nora, 'Board game night set', '450 kr', 'Something for the whole family', false);

  -- Map: public landmarks only.
  insert into public.user_locations (user_id, family_id, lat, lng, display_name, updated_at, visible) values
    (emma, family, 59.9075, 10.7531, 'Emma Nordmann', now() - interval '3 minutes', true),
    (lars, family, 59.9139, 10.7522, 'Lars Nordmann', now() - interval '8 minutes', true),
    (nora, family, 59.9270, 10.7008, 'Nora Nordmann', now() - interval '15 minutes', true)
  on conflict (user_id) do nothing;
end;
$$;
