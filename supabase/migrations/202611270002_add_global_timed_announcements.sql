create table if not exists public.global_announcements (
  id uuid primary key default gen_random_uuid(),
  body text not null check (char_length(btrim(body)) between 1 and 500),
  starts_at timestamptz not null default now(),
  expires_at timestamptz not null,
  created_by uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  constraint global_announcements_valid_window check (expires_at > starts_at)
);

alter table public.global_announcements enable row level security;
grant select, insert, update, delete on public.global_announcements to authenticated;

drop policy if exists "Authenticated users read active global announcements" on public.global_announcements;
create policy "Authenticated users read active global announcements"
on public.global_announcements for select to authenticated
using (starts_at <= now() and expires_at > now());

drop policy if exists "Primary admin creates global announcements" on public.global_announcements;
create policy "Primary admin creates global announcements"
on public.global_announcements for insert to authenticated
with check (
  created_by = (select auth.uid())
  and lower(coalesce((select auth.jwt()->>'email'),'')) = 'amyrmftah3@gmail.com'
);

drop policy if exists "Primary admin updates global announcements" on public.global_announcements;
create policy "Primary admin updates global announcements"
on public.global_announcements for update to authenticated
using (lower(coalesce((select auth.jwt()->>'email'),'')) = 'amyrmftah3@gmail.com')
with check (lower(coalesce((select auth.jwt()->>'email'),'')) = 'amyrmftah3@gmail.com');

drop policy if exists "Primary admin deletes global announcements" on public.global_announcements;
create policy "Primary admin deletes global announcements"
on public.global_announcements for delete to authenticated
using (lower(coalesce((select auth.jwt()->>'email'),'')) = 'amyrmftah3@gmail.com');
