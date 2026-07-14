-- Optional free-text description on wishes (shown in the member detail popup,
-- the owner editor, and the PDF export). Safe to re-run.
alter table public.wishes
  add column if not exists description text;
