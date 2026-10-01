alter table public.wishlists
  add column if not exists share_token uuid unique;

create table if not exists public.wishlist_shares (
  id uuid primary key default gen_random_uuid(),
  wishlist_id uuid not null references public.wishlists(id) on delete cascade,
  user_id uuid not null references public.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique (wishlist_id, user_id)
);
create index if not exists idx_wishlist_shares_user on public.wishlist_shares(user_id);
alter table public.wishlist_shares enable row level security;
grant select on public.wishlist_shares to authenticated;

drop policy if exists wishlist_shares_select on public.wishlist_shares;
create policy wishlist_shares_select on public.wishlist_shares
  for select using (user_id = public.my_app_user_id());

create or replace function public.ensure_wishlist_share_token(p_wishlist_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
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
grant execute on function public.ensure_wishlist_share_token(uuid) to authenticated;

create or replace function public.accept_wishlist_share(p_token uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
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
grant execute on function public.accept_wishlist_share(uuid) to authenticated;

drop policy if exists wishlists_select on public.wishlists;
create policy wishlists_select on public.wishlists
  for select using (
    owner_user_id = public.my_app_user_id()
    or family_id = public.my_family_id()
    or exists (
      select 1 from public.wishlist_shares s
      where s.wishlist_id = wishlists.id and s.user_id = public.my_app_user_id()
    )
  );

drop policy if exists wishes_shared_select on public.wishes;
create policy wishes_shared_select on public.wishes
  for select using (
    wishlist_id in (
      select wishlist_id from public.wishlist_shares
      where user_id = public.my_app_user_id()
    )
  );

drop policy if exists wish_reservations_select on public.wish_reservations;
create policy wish_reservations_select on public.wish_reservations
for select using (
  exists (
    select 1 from public.wishes w
    join public.wishlists wl on wl.id = w.wishlist_id
    where w.id = wish_reservations.wish_id
      and wl.family_id = (select family_id from public.users where auth_id = auth.uid() limit 1)
      and wl.owner_user_id <> (select id from public.users where auth_id = auth.uid() limit 1)
  )
  or exists (
    select 1 from public.wishes w
    join public.wishlist_shares s on s.wishlist_id = w.wishlist_id
    where w.id = wish_reservations.wish_id
      and s.user_id = (select id from public.users where auth_id = auth.uid() limit 1)
  )
);

drop policy if exists wish_reservations_insert on public.wish_reservations;
create policy wish_reservations_insert on public.wish_reservations
for insert with check (
  reserved_by = (select id from public.users where auth_id = auth.uid() limit 1)
  and (
    exists (
      select 1 from public.wishes w
      join public.wishlists wl on wl.id = w.wishlist_id
      where w.id = wish_reservations.wish_id
        and wl.family_id = (select family_id from public.users where auth_id = auth.uid() limit 1)
        and wl.owner_user_id <> (select id from public.users where auth_id = auth.uid() limit 1)
    )
    or exists (
      select 1 from public.wishes w
      join public.wishlist_shares s on s.wishlist_id = w.wishlist_id
      where w.id = wish_reservations.wish_id
        and s.user_id = (select id from public.users where auth_id = auth.uid() limit 1)
    )
  )
);;
