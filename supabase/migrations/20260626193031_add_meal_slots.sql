alter table public.meal_plan_days add column if not exists breakfast text not null default '';
alter table public.meal_plan_days add column if not exists lunch text not null default '';;
