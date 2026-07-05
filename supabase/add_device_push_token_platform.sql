-- Adds a platform discriminator to device push tokens so the edge functions can
-- shape FCM payloads per platform: Android stays data-only (client builds the
-- notification), iOS gets a notification block + apns section (data-only pushes
-- are background-throttled on iOS).
--
-- Safe to re-run.

alter table public.device_push_tokens
  add column if not exists platform text not null default 'android';

alter table public.device_push_tokens
  drop constraint if exists device_push_tokens_platform_check;

alter table public.device_push_tokens
  add constraint device_push_tokens_platform_check
  check (platform in ('android', 'ios'));
