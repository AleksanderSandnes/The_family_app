


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


CREATE SCHEMA IF NOT EXISTS "public";


ALTER SCHEMA "public" OWNER TO "pg_database_owner";


COMMENT ON SCHEMA "public" IS 'standard public schema';



CREATE OR REPLACE FUNCTION "public"."accept_wishlist_share"("p_token" "uuid") RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  me uuid;
  wl_id uuid;
  wl_owner uuid;
begin
  select id into me from public.users where auth_id = auth.uid() limit 1;
  if me is null then raise exception 'not authenticated'; end if;
  select id, owner_user_id into wl_id, wl_owner from public.wishlists
    where share_token = p_token and deleted_at is null limit 1;
  if wl_id is null then return null; end if;
  if wl_owner = me then return wl_id; end if;
  insert into public.wishlist_shares (wishlist_id, user_id)
    values (wl_id, me) on conflict (wishlist_id, user_id) do nothing;
  return wl_id;
end;
$$;


ALTER FUNCTION "public"."accept_wishlist_share"("p_token" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."enforce_family_id_change"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
begin
  if auth.uid() is null then
    return new;
  end if;
  if new.family_id is distinct from old.family_id and new.family_id is not null then
    if coalesce(current_setting('app.join_ok', true), '') <> '1'
       and not exists (
         select 1 from public.families f
         where f.id = new.family_id and f.admin_id = new.id
       ) then
      raise exception 'family_id can only be changed via join_family()'
        using errcode = 'insufficient_privilege';
    end if;
  end if;
  return new;
end;
$$;


ALTER FUNCTION "public"."enforce_family_id_change"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."enforce_family_member_update"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
begin
  if auth.uid() is null then
    return new;
  end if;
  if old.admin_id is distinct from public.my_app_user_id() then
    if new.name              is distinct from old.name
       or new.join_code         is distinct from old.join_code
       or new.admin_id          is distinct from old.admin_id
       or new.deleted_at        is distinct from old.deleted_at
       or new.last_join_used_at is distinct from old.last_join_used_at then
      raise exception 'only the family admin can change that'
        using errcode = 'insufficient_privilege';
    end if;
  end if;
  return new;
end;
$$;


ALTER FUNCTION "public"."enforce_family_member_update"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."ensure_conversation_participant"("conv_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  me_id     uuid;
  me_family uuid;
  conv_family uuid;
begin
  select id, family_id into me_id, me_family
  from public.users where auth_id = auth.uid() limit 1;

  if me_id is null then return; end if;

  select family_id into conv_family
  from public.conversations where id = conv_id;

  -- Only allow self-add if the conversation belongs to the same family
  if me_family is null or conv_family is null or me_family != conv_family then
    return;
  end if;

  insert into public.conversation_participants (conversation_id, user_id)
  values (conv_id, me_id)
  on conflict (conversation_id, user_id) do nothing;
end;
$$;


ALTER FUNCTION "public"."ensure_conversation_participant"("conv_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."ensure_wishlist_share_token"("p_wishlist_id" "uuid") RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  me uuid;
  tok uuid;
begin
  select id into me from public.users where auth_id = auth.uid() limit 1;
  if me is null then raise exception 'not authenticated'; end if;
  select share_token into tok from public.wishlists
    where id = p_wishlist_id and owner_user_id = me and deleted_at is null;
  if not found then
    raise exception 'not your wishlist' using errcode = 'insufficient_privilege';
  end if;
  if tok is null then
    tok := gen_random_uuid();
    update public.wishlists set share_token = tok where id = p_wishlist_id;
  end if;
  return tok;
end;
$$;


ALTER FUNCTION "public"."ensure_wishlist_share_token"("p_wishlist_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_new_auth_user"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
BEGIN
  INSERT INTO public.users (auth_id, name, email, mobile, birthday, avatar_color)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'full_name', split_part(NEW.email, '@', 1)),
    NEW.email,
    COALESCE(NEW.raw_user_meta_data->>'phone', ''),
    COALESCE(NEW.raw_user_meta_data->>'birthday', ''),
    COALESCE((NEW.raw_user_meta_data->>'avatar_color')::int, 0)
  )
  ON CONFLICT (auth_id) DO NOTHING;
  RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."handle_new_auth_user"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."i_am_family_admin"() RETURNS boolean
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
  select exists (
    select 1
    from public.families f
    where f.id = public.my_family_id()
      and f.admin_id = public.my_app_user_id()
  );
$$;


ALTER FUNCTION "public"."i_am_family_admin"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."is_conversation_member"("conv_id" "uuid", "uid" "uuid") RETURNS boolean
    LANGUAGE "sql" STABLE SECURITY DEFINER
    AS $$
  select exists (
    select 1 from public.conversation_participants
    where conversation_id = conv_id and user_id = uid
  );
$$;


ALTER FUNCTION "public"."is_conversation_member"("conv_id" "uuid", "uid" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."join_family"("p_code" "text") RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  me uuid;
  fam_id uuid;
begin
  select id into me from public.users where auth_id = auth.uid() limit 1;
  if me is null then
    raise exception 'not authenticated';
  end if;

  select id into fam_id from public.families
    where join_code = btrim(p_code) and deleted_at is null
    limit 1;
  if fam_id is null then
    return null;
  end if;

  perform set_config('app.join_ok', '1', true);
  update public.users set family_id = fam_id where id = me;
  perform set_config('app.join_ok', '', true);

  return fam_id;
end;
$$;


ALTER FUNCTION "public"."join_family"("p_code" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."my_app_user_id"() RETURNS "uuid"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$ select id from public.users where auth_id = auth.uid() limit 1; $$;


ALTER FUNCTION "public"."my_app_user_id"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."my_family_id"() RETURNS "uuid"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
  SELECT family_id FROM public.users WHERE auth_id = auth.uid() LIMIT 1;
$$;


ALTER FUNCTION "public"."my_family_id"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."rls_auto_enable"() RETURNS "event_trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'pg_catalog'
    AS $$
DECLARE
  cmd record;
BEGIN
  FOR cmd IN
    SELECT *
    FROM pg_event_trigger_ddl_commands()
    WHERE command_tag IN ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
      AND object_type IN ('table','partitioned table')
  LOOP
     IF cmd.schema_name IS NOT NULL AND cmd.schema_name IN ('public') AND cmd.schema_name NOT IN ('pg_catalog','information_schema') AND cmd.schema_name NOT LIKE 'pg_toast%' AND cmd.schema_name NOT LIKE 'pg_temp%' THEN
      BEGIN
        EXECUTE format('alter table if exists %s enable row level security', cmd.object_identity);
        RAISE LOG 'rls_auto_enable: enabled RLS on %', cmd.object_identity;
      EXCEPTION
        WHEN OTHERS THEN
          RAISE LOG 'rls_auto_enable: failed to enable RLS on %', cmd.object_identity;
      END;
     ELSE
        RAISE LOG 'rls_auto_enable: skip % (either system schema or not in enforced list: %.)', cmd.object_identity, cmd.schema_name;
     END IF;
  END LOOP;
END;
$$;


ALTER FUNCTION "public"."rls_auto_enable"() OWNER TO "postgres";

SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "public"."birthdays" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "date" "text" NOT NULL,
    "family_id" "uuid",
    "user_id" "uuid",
    "made_by_user_id" "uuid" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "deleted_at" timestamp with time zone,
    "icon" "text" DEFAULT 'cake'::"text" NOT NULL,
    "color" integer
);

ALTER TABLE ONLY "public"."birthdays" REPLICA IDENTITY FULL;


ALTER TABLE "public"."birthdays" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."calendar_events" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid" NOT NULL,
    "family_id" "uuid",
    "date_from" "text" NOT NULL,
    "date_to" "text" NOT NULL,
    "time_from" "text" DEFAULT ''::"text" NOT NULL,
    "time_to" "text" DEFAULT ''::"text" NOT NULL,
    "activity" "text" NOT NULL,
    "all_day" boolean DEFAULT false NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "deleted_at" timestamp with time zone,
    "icon" "text" DEFAULT 'schedule'::"text" NOT NULL,
    "is_private" boolean DEFAULT false NOT NULL,
    "color" integer,
    "attendee_ids" "text"[] DEFAULT '{}'::"text"[] NOT NULL
);

ALTER TABLE ONLY "public"."calendar_events" REPLICA IDENTITY FULL;


ALTER TABLE "public"."calendar_events" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."conversation_participants" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "conversation_id" "uuid" NOT NULL,
    "user_id" "uuid" NOT NULL,
    "joined_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "last_read_at" timestamp with time zone
);

ALTER TABLE ONLY "public"."conversation_participants" REPLICA IDENTITY FULL;


ALTER TABLE "public"."conversation_participants" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."conversations" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_from" "uuid" NOT NULL,
    "user_to" "uuid",
    "name" "text" DEFAULT ''::"text" NOT NULL,
    "family_id" "uuid",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "deleted_at" timestamp with time zone,
    "image_uri" "text"
);

ALTER TABLE ONLY "public"."conversations" REPLICA IDENTITY FULL;


ALTER TABLE "public"."conversations" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."device_push_tokens" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid" NOT NULL,
    "token" "text" NOT NULL,
    "platform" "text" DEFAULT 'android'::"text" NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "device_push_tokens_platform_check" CHECK (("platform" = ANY (ARRAY['android'::"text", 'ios'::"text"])))
);


ALTER TABLE "public"."device_push_tokens" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."families" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "join_code" "text" NOT NULL,
    "admin_id" "uuid",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "deleted_at" timestamp with time zone,
    "last_join_used_at" timestamp with time zone,
    "photo_url" "text"
);


ALTER TABLE "public"."families" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."family_relations" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "family_id" "uuid" NOT NULL,
    "from_user_id" "uuid" NOT NULL,
    "to_user_id" "uuid" NOT NULL,
    "relation" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."family_relations" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."meal_plan_days" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "meal_plan_id" "uuid" NOT NULL,
    "day" "text" NOT NULL,
    "date" "text" NOT NULL,
    "food" "text" DEFAULT ''::"text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "deleted_at" timestamp with time zone
);


ALTER TABLE "public"."meal_plan_days" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."meal_plans" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "family_id" "uuid" NOT NULL,
    "from_date" "text" NOT NULL,
    "to_date" "text" NOT NULL,
    "week" integer NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "deleted_at" timestamp with time zone,
    "name" "text" DEFAULT ''::"text" NOT NULL,
    "icon" "text" DEFAULT 'restaurant'::"text" NOT NULL,
    "color" integer,
    "created_by" "uuid"
);

ALTER TABLE ONLY "public"."meal_plans" REPLICA IDENTITY FULL;


ALTER TABLE "public"."meal_plans" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."message_reactions" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "message_id" "uuid" NOT NULL,
    "conversation_id" "uuid" NOT NULL,
    "user_id" "uuid" NOT NULL,
    "emoji" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"()
);

ALTER TABLE ONLY "public"."message_reactions" REPLICA IDENTITY FULL;


ALTER TABLE "public"."message_reactions" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."messages" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "conversation_id" "uuid" NOT NULL,
    "user_from" "uuid" NOT NULL,
    "text" "text" NOT NULL,
    "sent_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "deleted_at" timestamp with time zone,
    "reply_to_id" "uuid",
    "message_type" "text" DEFAULT 'text'::"text" NOT NULL,
    "media_url" "text",
    "edited_at" timestamp with time zone
);

ALTER TABLE ONLY "public"."messages" REPLICA IDENTITY FULL;


ALTER TABLE "public"."messages" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."shopping_items" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "list_id" "uuid" NOT NULL,
    "item" "text" NOT NULL,
    "checked" boolean DEFAULT false NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "deleted_at" timestamp with time zone
);

ALTER TABLE ONLY "public"."shopping_items" REPLICA IDENTITY FULL;


ALTER TABLE "public"."shopping_items" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."shopping_lists" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "title" "text" NOT NULL,
    "owner_user_id" "uuid" NOT NULL,
    "family_id" "uuid",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "deleted_at" timestamp with time zone,
    "icon" "text" DEFAULT 'shopping_cart'::"text" NOT NULL,
    "color" integer
);

ALTER TABLE ONLY "public"."shopping_lists" REPLICA IDENTITY FULL;


ALTER TABLE "public"."shopping_lists" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."user_locations" (
    "user_id" "uuid" NOT NULL,
    "family_id" "uuid",
    "lat" double precision NOT NULL,
    "lng" double precision NOT NULL,
    "display_name" "text" DEFAULT ''::"text" NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "visible" boolean DEFAULT false NOT NULL
);

ALTER TABLE ONLY "public"."user_locations" REPLICA IDENTITY FULL;


ALTER TABLE "public"."user_locations" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."users" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "auth_id" "uuid",
    "name" "text" NOT NULL,
    "email" "text" NOT NULL,
    "birthday" "text" DEFAULT ''::"text" NOT NULL,
    "mobile" "text" DEFAULT ''::"text" NOT NULL,
    "family_id" "uuid",
    "avatar_color" integer DEFAULT 0 NOT NULL,
    "avatar_url" "text",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "deleted_at" timestamp with time zone,
    "last_active_at" timestamp with time zone,
    "notifications_enabled" boolean DEFAULT true NOT NULL,
    "notify_days_before" integer DEFAULT 1 NOT NULL
);


ALTER TABLE "public"."users" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."wish_reservations" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "wish_id" "uuid" NOT NULL,
    "reserved_by" "uuid" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);

ALTER TABLE ONLY "public"."wish_reservations" REPLICA IDENTITY FULL;


ALTER TABLE "public"."wish_reservations" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."wishes" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "wishlist_id" "uuid" NOT NULL,
    "user_id" "uuid" NOT NULL,
    "text" "text" NOT NULL,
    "checked" boolean DEFAULT false NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "deleted_at" timestamp with time zone,
    "link" "text",
    "price" "text",
    "image_url" "text",
    "description" "text"
);


ALTER TABLE "public"."wishes" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."wishlist_shares" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "wishlist_id" "uuid" NOT NULL,
    "user_id" "uuid" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."wishlist_shares" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."wishlists" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "owner_user_id" "uuid" NOT NULL,
    "name" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "deleted_at" timestamp with time zone,
    "family_id" "uuid",
    "icon" "text" DEFAULT 'card_giftcard'::"text" NOT NULL,
    "color" integer,
    "share_token" "uuid"
);

ALTER TABLE ONLY "public"."wishlists" REPLICA IDENTITY FULL;


ALTER TABLE "public"."wishlists" OWNER TO "postgres";


ALTER TABLE ONLY "public"."birthdays"
    ADD CONSTRAINT "birthdays_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."calendar_events"
    ADD CONSTRAINT "calendar_events_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."conversation_participants"
    ADD CONSTRAINT "conversation_participants_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."conversations"
    ADD CONSTRAINT "conversations_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."device_push_tokens"
    ADD CONSTRAINT "device_push_tokens_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."device_push_tokens"
    ADD CONSTRAINT "device_push_tokens_token_key" UNIQUE ("token");



ALTER TABLE ONLY "public"."families"
    ADD CONSTRAINT "families_name_key" UNIQUE ("name");



ALTER TABLE ONLY "public"."families"
    ADD CONSTRAINT "families_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."family_relations"
    ADD CONSTRAINT "family_relations_from_user_id_to_user_id_key" UNIQUE ("from_user_id", "to_user_id");



ALTER TABLE ONLY "public"."family_relations"
    ADD CONSTRAINT "family_relations_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."meal_plan_days"
    ADD CONSTRAINT "meal_plan_days_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."meal_plans"
    ADD CONSTRAINT "meal_plans_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."message_reactions"
    ADD CONSTRAINT "message_reactions_message_id_user_id_key" UNIQUE ("message_id", "user_id");



ALTER TABLE ONLY "public"."message_reactions"
    ADD CONSTRAINT "message_reactions_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."messages"
    ADD CONSTRAINT "messages_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."shopping_items"
    ADD CONSTRAINT "shopping_items_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."shopping_lists"
    ADD CONSTRAINT "shopping_lists_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."conversation_participants"
    ADD CONSTRAINT "uq_conversation_participant" UNIQUE ("conversation_id", "user_id");



ALTER TABLE ONLY "public"."user_locations"
    ADD CONSTRAINT "user_locations_pkey" PRIMARY KEY ("user_id");



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_auth_id_key" UNIQUE ("auth_id");



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_email_key" UNIQUE ("email");



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."wish_reservations"
    ADD CONSTRAINT "wish_reservations_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."wish_reservations"
    ADD CONSTRAINT "wish_reservations_wish_id_key" UNIQUE ("wish_id");



ALTER TABLE ONLY "public"."wishes"
    ADD CONSTRAINT "wishes_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."wishlist_shares"
    ADD CONSTRAINT "wishlist_shares_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."wishlist_shares"
    ADD CONSTRAINT "wishlist_shares_wishlist_id_user_id_key" UNIQUE ("wishlist_id", "user_id");



ALTER TABLE ONLY "public"."wishlists"
    ADD CONSTRAINT "wishlists_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."wishlists"
    ADD CONSTRAINT "wishlists_share_token_key" UNIQUE ("share_token");



CREATE INDEX "idx_birthdays_family" ON "public"."birthdays" USING "btree" ("family_id");



CREATE INDEX "idx_calendar_events_user" ON "public"."calendar_events" USING "btree" ("user_id");



CREATE INDEX "idx_conv_participants_conv" ON "public"."conversation_participants" USING "btree" ("conversation_id");



CREATE INDEX "idx_conv_participants_user" ON "public"."conversation_participants" USING "btree" ("user_id");



CREATE INDEX "idx_conversations_users" ON "public"."conversations" USING "btree" ("user_from", "user_to");



CREATE INDEX "idx_device_push_tokens_user" ON "public"."device_push_tokens" USING "btree" ("user_id");



CREATE INDEX "idx_meal_plans_family" ON "public"."meal_plans" USING "btree" ("family_id");



CREATE INDEX "idx_message_reactions_conversation_id" ON "public"."message_reactions" USING "btree" ("conversation_id");



CREATE INDEX "idx_message_reactions_message_id" ON "public"."message_reactions" USING "btree" ("message_id");



CREATE INDEX "idx_messages_conversation" ON "public"."messages" USING "btree" ("conversation_id");



CREATE INDEX "idx_messages_reply_to" ON "public"."messages" USING "btree" ("reply_to_id");



CREATE INDEX "idx_shopping_items_list" ON "public"."shopping_items" USING "btree" ("list_id");



CREATE INDEX "idx_shopping_lists_owner" ON "public"."shopping_lists" USING "btree" ("owner_user_id");



CREATE INDEX "idx_users_auth_id" ON "public"."users" USING "btree" ("auth_id");



CREATE INDEX "idx_users_family_id" ON "public"."users" USING "btree" ("family_id");



CREATE INDEX "idx_wish_reservations_wish" ON "public"."wish_reservations" USING "btree" ("wish_id");



CREATE INDEX "idx_wishlist_shares_user" ON "public"."wishlist_shares" USING "btree" ("user_id");



CREATE INDEX "idx_wishlists_family" ON "public"."wishlists" USING "btree" ("family_id");



CREATE INDEX "idx_wishlists_owner" ON "public"."wishlists" USING "btree" ("owner_user_id");



-- Push webhook is installed separately from post_deploy/push_webhook.sql after Vault provisioning.



CREATE OR REPLACE TRIGGER "trg_enforce_family_id_change" BEFORE UPDATE OF "family_id" ON "public"."users" FOR EACH ROW EXECUTE FUNCTION "public"."enforce_family_id_change"();



CREATE OR REPLACE TRIGGER "trg_enforce_family_member_update" BEFORE UPDATE ON "public"."families" FOR EACH ROW EXECUTE FUNCTION "public"."enforce_family_member_update"();



ALTER TABLE ONLY "public"."birthdays"
    ADD CONSTRAINT "birthdays_family_id_fkey" FOREIGN KEY ("family_id") REFERENCES "public"."families"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."birthdays"
    ADD CONSTRAINT "birthdays_made_by_user_id_fkey" FOREIGN KEY ("made_by_user_id") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."birthdays"
    ADD CONSTRAINT "birthdays_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."calendar_events"
    ADD CONSTRAINT "calendar_events_family_id_fkey" FOREIGN KEY ("family_id") REFERENCES "public"."families"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."calendar_events"
    ADD CONSTRAINT "calendar_events_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."conversation_participants"
    ADD CONSTRAINT "conversation_participants_conversation_id_fkey" FOREIGN KEY ("conversation_id") REFERENCES "public"."conversations"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."conversation_participants"
    ADD CONSTRAINT "conversation_participants_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."conversations"
    ADD CONSTRAINT "conversations_family_id_fkey" FOREIGN KEY ("family_id") REFERENCES "public"."families"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."conversations"
    ADD CONSTRAINT "conversations_user_from_fkey" FOREIGN KEY ("user_from") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."conversations"
    ADD CONSTRAINT "conversations_user_to_fkey" FOREIGN KEY ("user_to") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."device_push_tokens"
    ADD CONSTRAINT "device_push_tokens_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."families"
    ADD CONSTRAINT "families_admin_id_fkey" FOREIGN KEY ("admin_id") REFERENCES "public"."users"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."family_relations"
    ADD CONSTRAINT "family_relations_family_id_fkey" FOREIGN KEY ("family_id") REFERENCES "public"."families"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."family_relations"
    ADD CONSTRAINT "family_relations_from_user_id_fkey" FOREIGN KEY ("from_user_id") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."family_relations"
    ADD CONSTRAINT "family_relations_to_user_id_fkey" FOREIGN KEY ("to_user_id") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."meal_plan_days"
    ADD CONSTRAINT "meal_plan_days_meal_plan_id_fkey" FOREIGN KEY ("meal_plan_id") REFERENCES "public"."meal_plans"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."meal_plans"
    ADD CONSTRAINT "meal_plans_created_by_fkey" FOREIGN KEY ("created_by") REFERENCES "public"."users"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."meal_plans"
    ADD CONSTRAINT "meal_plans_family_id_fkey" FOREIGN KEY ("family_id") REFERENCES "public"."families"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."message_reactions"
    ADD CONSTRAINT "message_reactions_conversation_id_fkey" FOREIGN KEY ("conversation_id") REFERENCES "public"."conversations"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."message_reactions"
    ADD CONSTRAINT "message_reactions_message_id_fkey" FOREIGN KEY ("message_id") REFERENCES "public"."messages"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."message_reactions"
    ADD CONSTRAINT "message_reactions_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."messages"
    ADD CONSTRAINT "messages_conversation_id_fkey" FOREIGN KEY ("conversation_id") REFERENCES "public"."conversations"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."messages"
    ADD CONSTRAINT "messages_reply_to_id_fkey" FOREIGN KEY ("reply_to_id") REFERENCES "public"."messages"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."messages"
    ADD CONSTRAINT "messages_user_from_fkey" FOREIGN KEY ("user_from") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."shopping_items"
    ADD CONSTRAINT "shopping_items_list_id_fkey" FOREIGN KEY ("list_id") REFERENCES "public"."shopping_lists"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."shopping_lists"
    ADD CONSTRAINT "shopping_lists_family_id_fkey" FOREIGN KEY ("family_id") REFERENCES "public"."families"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."shopping_lists"
    ADD CONSTRAINT "shopping_lists_owner_user_id_fkey" FOREIGN KEY ("owner_user_id") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."user_locations"
    ADD CONSTRAINT "user_locations_family_id_fkey" FOREIGN KEY ("family_id") REFERENCES "public"."families"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."user_locations"
    ADD CONSTRAINT "user_locations_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_auth_id_fkey" FOREIGN KEY ("auth_id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_family_id_fkey" FOREIGN KEY ("family_id") REFERENCES "public"."families"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."wish_reservations"
    ADD CONSTRAINT "wish_reservations_reserved_by_fkey" FOREIGN KEY ("reserved_by") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."wish_reservations"
    ADD CONSTRAINT "wish_reservations_wish_id_fkey" FOREIGN KEY ("wish_id") REFERENCES "public"."wishes"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."wishes"
    ADD CONSTRAINT "wishes_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."wishes"
    ADD CONSTRAINT "wishes_wishlist_id_fkey" FOREIGN KEY ("wishlist_id") REFERENCES "public"."wishlists"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."wishlist_shares"
    ADD CONSTRAINT "wishlist_shares_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."wishlist_shares"
    ADD CONSTRAINT "wishlist_shares_wishlist_id_fkey" FOREIGN KEY ("wishlist_id") REFERENCES "public"."wishlists"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."wishlists"
    ADD CONSTRAINT "wishlists_family_id_fkey" FOREIGN KEY ("family_id") REFERENCES "public"."families"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."wishlists"
    ADD CONSTRAINT "wishlists_owner_user_id_fkey" FOREIGN KEY ("owner_user_id") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE "public"."birthdays" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "birthdays_delete" ON "public"."birthdays" FOR DELETE USING (("made_by_user_id" = "public"."my_app_user_id"()));



CREATE POLICY "birthdays_insert" ON "public"."birthdays" FOR INSERT WITH CHECK (("made_by_user_id" = "public"."my_app_user_id"()));



CREATE POLICY "birthdays_select" ON "public"."birthdays" FOR SELECT USING ((("made_by_user_id" = "public"."my_app_user_id"()) OR ("family_id" = "public"."my_family_id"())));



CREATE POLICY "birthdays_update" ON "public"."birthdays" FOR UPDATE USING (("made_by_user_id" = "public"."my_app_user_id"())) WITH CHECK (("made_by_user_id" = "public"."my_app_user_id"()));



ALTER TABLE "public"."calendar_events" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "calendar_events_delete" ON "public"."calendar_events" FOR DELETE USING ((("user_id" = "public"."my_app_user_id"()) OR (("family_id" = "public"."my_family_id"()) AND (COALESCE("is_private", false) = false) AND "public"."i_am_family_admin"())));



CREATE POLICY "calendar_events_insert" ON "public"."calendar_events" FOR INSERT WITH CHECK (("user_id" = "public"."my_app_user_id"()));



CREATE POLICY "calendar_events_select" ON "public"."calendar_events" FOR SELECT USING ((("user_id" = "public"."my_app_user_id"()) OR (("family_id" = "public"."my_family_id"()) AND (COALESCE("is_private", false) = false))));



CREATE POLICY "calendar_events_update" ON "public"."calendar_events" FOR UPDATE USING ((("user_id" = "public"."my_app_user_id"()) OR (("family_id" = "public"."my_family_id"()) AND (COALESCE("is_private", false) = false)))) WITH CHECK ((("user_id" = "public"."my_app_user_id"()) OR (("family_id" = "public"."my_family_id"()) AND (COALESCE("is_private", false) = false))));



CREATE POLICY "conv_participants_delete" ON "public"."conversation_participants" FOR DELETE USING ((("user_id" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1)) OR "public"."is_conversation_member"("conversation_id", ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1))));



CREATE POLICY "conv_participants_insert" ON "public"."conversation_participants" FOR INSERT WITH CHECK (((EXISTS ( SELECT 1
   FROM "public"."conversations" "c"
  WHERE (("c"."id" = "conversation_participants"."conversation_id") AND ("c"."user_from" = ( SELECT "users"."id"
           FROM "public"."users"
          WHERE ("users"."auth_id" = "auth"."uid"())
         LIMIT 1))))) OR "public"."is_conversation_member"("conversation_id", ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1))));



CREATE POLICY "conv_participants_select" ON "public"."conversation_participants" FOR SELECT USING ("public"."is_conversation_member"("conversation_id", ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1)));



CREATE POLICY "conv_participants_update" ON "public"."conversation_participants" FOR UPDATE USING (("user_id" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1))) WITH CHECK (("user_id" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1)));



ALTER TABLE "public"."conversation_participants" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."conversations" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "conversations_delete" ON "public"."conversations" FOR DELETE USING ((("user_from" = "public"."my_app_user_id"()) OR ("public"."is_conversation_member"("id", "public"."my_app_user_id"()) AND "public"."i_am_family_admin"())));



CREATE POLICY "conversations_insert" ON "public"."conversations" FOR INSERT WITH CHECK (("user_from" = "public"."my_app_user_id"()));



CREATE POLICY "conversations_select" ON "public"."conversations" FOR SELECT USING ((("user_from" = "public"."my_app_user_id"()) OR "public"."is_conversation_member"("id", "public"."my_app_user_id"())));



CREATE POLICY "conversations_update" ON "public"."conversations" FOR UPDATE USING ((("user_from" = "public"."my_app_user_id"()) OR "public"."is_conversation_member"("id", "public"."my_app_user_id"()))) WITH CHECK ((("user_from" = "public"."my_app_user_id"()) OR "public"."is_conversation_member"("id", "public"."my_app_user_id"())));



ALTER TABLE "public"."device_push_tokens" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "device_push_tokens_delete" ON "public"."device_push_tokens" FOR DELETE USING (("user_id" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1)));



CREATE POLICY "device_push_tokens_insert" ON "public"."device_push_tokens" FOR INSERT WITH CHECK (("user_id" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1)));



CREATE POLICY "device_push_tokens_select" ON "public"."device_push_tokens" FOR SELECT USING (("user_id" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1)));



CREATE POLICY "device_push_tokens_update" ON "public"."device_push_tokens" FOR UPDATE USING (("user_id" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1))) WITH CHECK (("user_id" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1)));



ALTER TABLE "public"."families" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "families_insert" ON "public"."families" FOR INSERT WITH CHECK (("auth"."uid"() IS NOT NULL));



CREATE POLICY "families_select" ON "public"."families" FOR SELECT USING ((("id" = "public"."my_family_id"()) OR ("admin_id" = "public"."my_app_user_id"())));



CREATE POLICY "families_update" ON "public"."families" FOR UPDATE USING (("admin_id" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1)));



CREATE POLICY "families_update_member" ON "public"."families" FOR UPDATE USING (("id" = ( SELECT "users"."family_id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1)));



ALTER TABLE "public"."family_relations" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "family_relations delete" ON "public"."family_relations" FOR DELETE USING (("from_user_id" = "public"."my_app_user_id"()));



CREATE POLICY "family_relations insert" ON "public"."family_relations" FOR INSERT WITH CHECK ((("from_user_id" = "public"."my_app_user_id"()) AND ("family_id" = "public"."my_family_id"())));



CREATE POLICY "family_relations select" ON "public"."family_relations" FOR SELECT USING (("family_id" = "public"."my_family_id"()));



CREATE POLICY "family_relations update" ON "public"."family_relations" FOR UPDATE USING (("from_user_id" = "public"."my_app_user_id"()));



CREATE POLICY "manage_own_reactions" ON "public"."message_reactions" USING (("user_id" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1))) WITH CHECK (("user_id" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1)));



ALTER TABLE "public"."meal_plan_days" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "meal_plan_days_access" ON "public"."meal_plan_days" USING (("meal_plan_id" IN ( SELECT "meal_plans"."id"
   FROM "public"."meal_plans"
  WHERE ("meal_plans"."family_id" = ( SELECT "users"."family_id"
           FROM "public"."users"
          WHERE ("users"."auth_id" = "auth"."uid"())
         LIMIT 1)))));



ALTER TABLE "public"."meal_plans" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "meal_plans_delete" ON "public"."meal_plans" FOR DELETE USING ((("family_id" = "public"."my_family_id"()) AND (("created_by" = "public"."my_app_user_id"()) OR "public"."i_am_family_admin"())));



CREATE POLICY "meal_plans_insert" ON "public"."meal_plans" FOR INSERT WITH CHECK ((("family_id" = "public"."my_family_id"()) AND (("created_by" IS NULL) OR ("created_by" = "public"."my_app_user_id"()))));



CREATE POLICY "meal_plans_select" ON "public"."meal_plans" FOR SELECT USING (("family_id" = "public"."my_family_id"()));



CREATE POLICY "meal_plans_update" ON "public"."meal_plans" FOR UPDATE USING (("family_id" = "public"."my_family_id"())) WITH CHECK (("family_id" = "public"."my_family_id"()));



ALTER TABLE "public"."message_reactions" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."messages" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "messages_delete" ON "public"."messages" FOR DELETE USING (("user_from" = "public"."my_app_user_id"()));



CREATE POLICY "messages_insert" ON "public"."messages" FOR INSERT WITH CHECK ((("user_from" = "public"."my_app_user_id"()) AND "public"."is_conversation_member"("conversation_id", "public"."my_app_user_id"())));



CREATE POLICY "messages_select" ON "public"."messages" FOR SELECT USING ("public"."is_conversation_member"("conversation_id", "public"."my_app_user_id"()));



CREATE POLICY "messages_update" ON "public"."messages" FOR UPDATE USING (("user_from" = "public"."my_app_user_id"())) WITH CHECK (("user_from" = "public"."my_app_user_id"()));



ALTER TABLE "public"."shopping_items" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "shopping_items_access" ON "public"."shopping_items" USING (("list_id" IN ( SELECT "shopping_lists"."id"
   FROM "public"."shopping_lists"
  WHERE (("shopping_lists"."owner_user_id" = ( SELECT "users"."id"
           FROM "public"."users"
          WHERE ("users"."auth_id" = "auth"."uid"())
         LIMIT 1)) OR ("shopping_lists"."family_id" = ( SELECT "users"."family_id"
           FROM "public"."users"
          WHERE ("users"."auth_id" = "auth"."uid"())
         LIMIT 1))))));



ALTER TABLE "public"."shopping_lists" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "shopping_lists_delete" ON "public"."shopping_lists" FOR DELETE USING ((("owner_user_id" = "public"."my_app_user_id"()) OR (("family_id" = "public"."my_family_id"()) AND "public"."i_am_family_admin"())));



CREATE POLICY "shopping_lists_insert" ON "public"."shopping_lists" FOR INSERT WITH CHECK (("owner_user_id" = "public"."my_app_user_id"()));



CREATE POLICY "shopping_lists_select" ON "public"."shopping_lists" FOR SELECT USING ((("owner_user_id" = "public"."my_app_user_id"()) OR ("family_id" = "public"."my_family_id"())));



CREATE POLICY "shopping_lists_update" ON "public"."shopping_lists" FOR UPDATE USING ((("owner_user_id" = "public"."my_app_user_id"()) OR ("family_id" = "public"."my_family_id"()))) WITH CHECK ((("owner_user_id" = "public"."my_app_user_id"()) OR ("family_id" = "public"."my_family_id"())));



ALTER TABLE "public"."user_locations" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "user_locations_family_read" ON "public"."user_locations" FOR SELECT USING ((("visible" = true) AND ("family_id" IS NOT NULL) AND ("family_id" = ( SELECT "users"."family_id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1))));



CREATE POLICY "user_locations_own" ON "public"."user_locations" USING (("user_id" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1))) WITH CHECK (("user_id" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1)));



ALTER TABLE "public"."users" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "users delete" ON "public"."users" FOR DELETE USING (("auth_id" = "auth"."uid"()));



CREATE POLICY "users insert" ON "public"."users" FOR INSERT WITH CHECK (("auth_id" = "auth"."uid"()));



CREATE POLICY "users select" ON "public"."users" FOR SELECT USING ((("auth_id" = "auth"."uid"()) OR (("family_id" IS NOT NULL) AND ("family_id" = "public"."my_family_id"()))));



CREATE POLICY "users update" ON "public"."users" FOR UPDATE USING (("auth_id" = "auth"."uid"()));



CREATE POLICY "view_reactions" ON "public"."message_reactions" FOR SELECT USING ((EXISTS ( SELECT 1
   FROM "public"."conversation_participants" "cp"
  WHERE (("cp"."conversation_id" = "message_reactions"."conversation_id") AND ("cp"."user_id" = ( SELECT "users"."id"
           FROM "public"."users"
          WHERE ("users"."auth_id" = "auth"."uid"())
         LIMIT 1))))));



ALTER TABLE "public"."wish_reservations" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "wish_reservations_delete" ON "public"."wish_reservations" FOR DELETE USING (("reserved_by" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1)));



CREATE POLICY "wish_reservations_insert" ON "public"."wish_reservations" FOR INSERT WITH CHECK ((("reserved_by" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = "auth"."uid"())
 LIMIT 1)) AND ((EXISTS ( SELECT 1
   FROM ("public"."wishes" "w"
     JOIN "public"."wishlists" "wl" ON (("wl"."id" = "w"."wishlist_id")))
  WHERE (("w"."id" = "wish_reservations"."wish_id") AND ("wl"."family_id" = ( SELECT "users"."family_id"
           FROM "public"."users"
          WHERE ("users"."auth_id" = "auth"."uid"())
         LIMIT 1)) AND ("wl"."owner_user_id" <> ( SELECT "users"."id"
           FROM "public"."users"
          WHERE ("users"."auth_id" = "auth"."uid"())
         LIMIT 1))))) OR (EXISTS ( SELECT 1
   FROM ("public"."wishes" "w"
     JOIN "public"."wishlist_shares" "s" ON (("s"."wishlist_id" = "w"."wishlist_id")))
  WHERE (("w"."id" = "wish_reservations"."wish_id") AND ("s"."user_id" = ( SELECT "users"."id"
           FROM "public"."users"
          WHERE ("users"."auth_id" = "auth"."uid"())
         LIMIT 1))))))));



CREATE POLICY "wish_reservations_select" ON "public"."wish_reservations" FOR SELECT USING (((EXISTS ( SELECT 1
   FROM ("public"."wishes" "w"
     JOIN "public"."wishlists" "wl" ON (("wl"."id" = "w"."wishlist_id")))
  WHERE (("w"."id" = "wish_reservations"."wish_id") AND ("wl"."family_id" = ( SELECT "users"."family_id"
           FROM "public"."users"
          WHERE ("users"."auth_id" = "auth"."uid"())
         LIMIT 1)) AND ("wl"."owner_user_id" <> ( SELECT "users"."id"
           FROM "public"."users"
          WHERE ("users"."auth_id" = "auth"."uid"())
         LIMIT 1))))) OR (EXISTS ( SELECT 1
   FROM ("public"."wishes" "w"
     JOIN "public"."wishlist_shares" "s" ON (("s"."wishlist_id" = "w"."wishlist_id")))
  WHERE (("w"."id" = "wish_reservations"."wish_id") AND ("s"."user_id" = ( SELECT "users"."id"
           FROM "public"."users"
          WHERE ("users"."auth_id" = "auth"."uid"())
         LIMIT 1)))))));



ALTER TABLE "public"."wishes" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "wishes_access" ON "public"."wishes" USING (("wishlist_id" IN ( SELECT "wishlists"."id"
   FROM "public"."wishlists"
  WHERE (("wishlists"."owner_user_id" = ( SELECT "users"."id"
           FROM "public"."users"
          WHERE ("users"."auth_id" = "auth"."uid"())
         LIMIT 1)) OR ("wishlists"."family_id" = ( SELECT "users"."family_id"
           FROM "public"."users"
          WHERE ("users"."auth_id" = "auth"."uid"())
         LIMIT 1))))));



CREATE POLICY "wishes_shared_select" ON "public"."wishes" FOR SELECT USING (("wishlist_id" IN ( SELECT "wishlist_shares"."wishlist_id"
   FROM "public"."wishlist_shares"
  WHERE ("wishlist_shares"."user_id" = "public"."my_app_user_id"()))));



ALTER TABLE "public"."wishlist_shares" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "wishlist_shares_select" ON "public"."wishlist_shares" FOR SELECT USING (("user_id" = "public"."my_app_user_id"()));



ALTER TABLE "public"."wishlists" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "wishlists_delete" ON "public"."wishlists" FOR DELETE USING (("owner_user_id" = "public"."my_app_user_id"()));



CREATE POLICY "wishlists_insert" ON "public"."wishlists" FOR INSERT WITH CHECK (("owner_user_id" = "public"."my_app_user_id"()));



CREATE POLICY "wishlists_select" ON "public"."wishlists" FOR SELECT USING ((("owner_user_id" = "public"."my_app_user_id"()) OR ("family_id" = "public"."my_family_id"()) OR (EXISTS ( SELECT 1
   FROM "public"."wishlist_shares" "s"
  WHERE (("s"."wishlist_id" = "wishlists"."id") AND ("s"."user_id" = "public"."my_app_user_id"()))))));



CREATE POLICY "wishlists_update" ON "public"."wishlists" FOR UPDATE USING (("owner_user_id" = "public"."my_app_user_id"())) WITH CHECK (("owner_user_id" = "public"."my_app_user_id"()));



GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";



GRANT ALL ON FUNCTION "public"."accept_wishlist_share"("p_token" "uuid") TO "service_role";
GRANT ALL ON FUNCTION "public"."accept_wishlist_share"("p_token" "uuid") TO "authenticated";



GRANT ALL ON FUNCTION "public"."enforce_family_id_change"() TO "service_role";



GRANT ALL ON FUNCTION "public"."enforce_family_member_update"() TO "service_role";



GRANT ALL ON FUNCTION "public"."ensure_conversation_participant"("conv_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."ensure_wishlist_share_token"("p_wishlist_id" "uuid") TO "service_role";
GRANT ALL ON FUNCTION "public"."ensure_wishlist_share_token"("p_wishlist_id" "uuid") TO "authenticated";



GRANT ALL ON FUNCTION "public"."handle_new_auth_user"() TO "service_role";



GRANT ALL ON FUNCTION "public"."i_am_family_admin"() TO "service_role";



GRANT ALL ON FUNCTION "public"."is_conversation_member"("conv_id" "uuid", "uid" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."join_family"("p_code" "text") TO "service_role";
GRANT ALL ON FUNCTION "public"."join_family"("p_code" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."join_family"("p_code" "text") TO "anon";



GRANT ALL ON FUNCTION "public"."my_app_user_id"() TO "service_role";



GRANT ALL ON FUNCTION "public"."my_family_id"() TO "service_role";



GRANT ALL ON FUNCTION "public"."rls_auto_enable"() TO "service_role";



GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."birthdays" TO "anon";
GRANT ALL ON TABLE "public"."birthdays" TO "authenticated";
GRANT ALL ON TABLE "public"."birthdays" TO "service_role";



GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."calendar_events" TO "anon";
GRANT ALL ON TABLE "public"."calendar_events" TO "authenticated";
GRANT ALL ON TABLE "public"."calendar_events" TO "service_role";



GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."conversation_participants" TO "anon";
GRANT ALL ON TABLE "public"."conversation_participants" TO "authenticated";
GRANT ALL ON TABLE "public"."conversation_participants" TO "service_role";



GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."conversations" TO "anon";
GRANT ALL ON TABLE "public"."conversations" TO "authenticated";
GRANT ALL ON TABLE "public"."conversations" TO "service_role";



GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."device_push_tokens" TO "anon";
GRANT ALL ON TABLE "public"."device_push_tokens" TO "authenticated";
GRANT ALL ON TABLE "public"."device_push_tokens" TO "service_role";



GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."families" TO "anon";
GRANT ALL ON TABLE "public"."families" TO "authenticated";
GRANT ALL ON TABLE "public"."families" TO "service_role";



GRANT SELECT,REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."family_relations" TO "anon";
GRANT ALL ON TABLE "public"."family_relations" TO "authenticated";
GRANT ALL ON TABLE "public"."family_relations" TO "service_role";



GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."meal_plan_days" TO "anon";
GRANT ALL ON TABLE "public"."meal_plan_days" TO "authenticated";
GRANT ALL ON TABLE "public"."meal_plan_days" TO "service_role";



GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."meal_plans" TO "anon";
GRANT ALL ON TABLE "public"."meal_plans" TO "authenticated";
GRANT ALL ON TABLE "public"."meal_plans" TO "service_role";



GRANT SELECT,REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."message_reactions" TO "anon";
GRANT ALL ON TABLE "public"."message_reactions" TO "authenticated";
GRANT ALL ON TABLE "public"."message_reactions" TO "service_role";



GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."messages" TO "anon";
GRANT ALL ON TABLE "public"."messages" TO "authenticated";
GRANT ALL ON TABLE "public"."messages" TO "service_role";



GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."shopping_items" TO "anon";
GRANT ALL ON TABLE "public"."shopping_items" TO "authenticated";
GRANT ALL ON TABLE "public"."shopping_items" TO "service_role";



GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."shopping_lists" TO "anon";
GRANT ALL ON TABLE "public"."shopping_lists" TO "authenticated";
GRANT ALL ON TABLE "public"."shopping_lists" TO "service_role";



GRANT SELECT,REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."user_locations" TO "anon";
GRANT ALL ON TABLE "public"."user_locations" TO "authenticated";
GRANT ALL ON TABLE "public"."user_locations" TO "service_role";



GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."users" TO "anon";
GRANT ALL ON TABLE "public"."users" TO "authenticated";
GRANT ALL ON TABLE "public"."users" TO "service_role";



GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."wish_reservations" TO "anon";
GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."wish_reservations" TO "authenticated";
GRANT ALL ON TABLE "public"."wish_reservations" TO "service_role";



GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."wishes" TO "anon";
GRANT ALL ON TABLE "public"."wishes" TO "authenticated";
GRANT ALL ON TABLE "public"."wishes" TO "service_role";



GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."wishlist_shares" TO "anon";
GRANT SELECT,REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."wishlist_shares" TO "authenticated";
GRANT ALL ON TABLE "public"."wishlist_shares" TO "service_role";



GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."wishlists" TO "anon";
GRANT ALL ON TABLE "public"."wishlists" TO "authenticated";
GRANT ALL ON TABLE "public"."wishlists" TO "service_role";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "service_role";







