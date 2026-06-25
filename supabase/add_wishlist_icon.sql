alter table public.wishlists
    add column if not exists icon text not null default 'card_giftcard';
