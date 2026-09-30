create or replace function public.join_family(p_code text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
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

grant execute on function public.join_family(text) to authenticated, anon;

create or replace function public.enforce_family_id_change()
returns trigger
language plpgsql
as $$
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

drop trigger if exists trg_enforce_family_id_change on public.users;
create trigger trg_enforce_family_id_change
  before update of family_id on public.users
  for each row execute function public.enforce_family_id_change();

drop policy if exists families_select on public.families;
create policy families_select on public.families
  for select
  using (
    id = public.my_family_id()
    or admin_id = public.my_app_user_id()
  );

create or replace function public.enforce_family_member_update()
returns trigger
language plpgsql
as $$
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

drop trigger if exists trg_enforce_family_member_update on public.families;
create trigger trg_enforce_family_member_update
  before update on public.families
  for each row execute function public.enforce_family_member_update();

drop policy if exists messages_access on public.messages;
create policy messages_access on public.messages
  for all
  using (
    public.is_conversation_member(conversation_id, public.my_app_user_id())
    or exists (
      select 1 from public.conversations c
      where c.id = messages.conversation_id and c.user_from = public.my_app_user_id()
    )
  )
  with check (
    user_from = public.my_app_user_id()
    and (
      public.is_conversation_member(conversation_id, public.my_app_user_id())
      or exists (
        select 1 from public.conversations c
        where c.id = messages.conversation_id and c.user_from = public.my_app_user_id()
      )
    )
  );;
