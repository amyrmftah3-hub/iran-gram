-- IranGram content moderation and 10-hour expiry for flagged text messages.
-- Apply this migration in the Supabase SQL editor before enabling the policy in production.

alter table public.messages
  add column if not exists moderation_reason text,
  add column if not exists expires_at timestamptz;

create or replace function public.ig_moderate_message_text()
returns trigger
language plpgsql
set search_path = public
as $$
declare
  normalized_text text;
begin
  normalized_text := lower(coalesce(new.body, ''));
  normalized_text := translate(normalized_text, 'يك', 'یک');

  -- Text-only heuristic moderation. It is intentionally conservative and
  -- does not claim to inspect image, audio, or video bytes.
  if normalized_text ~ '(کص|کسکش|کیر|جنده|حرومزاده|مادر.?جنده|بی.?ناموس|گوه|ریدی|fuck|motherfucker|bitch|cunt|dick|porn|xxx|سکس|سکسی|پورن|پورنو|برهنه|نود|نودز|شهوت|اندام.?جنسی)' then
    new.moderation_reason := 'profanity_or_explicit_text';
    new.expires_at := coalesce(new.expires_at, now() + interval '10 hours');
  end if;

  return new;
end;
$$;

drop trigger if exists ig_moderate_message_text_before_insert on public.messages;
create trigger ig_moderate_message_text_before_insert
before insert on public.messages
for each row execute function public.ig_moderate_message_text();

-- Keep deletion server-side so it continues when every client is closed.
create extension if not exists pg_cron;

do $$
begin
  if not exists (select 1 from cron.job where jobname = 'irangram-delete-expired-moderated-messages') then
    perform cron.schedule(
      'irangram-delete-expired-moderated-messages',
      '* * * * *',
      $job$delete from public.messages
           where moderation_reason is not null
             and expires_at is not null
             and expires_at <= now();$job$
    );
  end if;
end;
$$;

-- Also clean up any flagged rows that may already be past their expiry.
delete from public.messages
where moderation_reason is not null
  and expires_at is not null
  and expires_at <= now();


-- User reporting for messages. Reports are private and deduplicated per user/message.
create table if not exists public.message_reports (
  id uuid primary key default gen_random_uuid(),
  message_id uuid not null references public.messages(id) on delete cascade,
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  reporter_id uuid not null references auth.users(id) on delete cascade,
  reason text not null default 'inappropriate_content',
  created_at timestamptz not null default now(),
  unique (message_id, reporter_id)
);

alter table public.message_reports enable row level security;

drop policy if exists "Members can report messages" on public.message_reports;
create policy "Members can report messages"
on public.message_reports
for insert
to authenticated
with check (
  reporter_id = auth.uid()
  and exists (
    select 1
    from public.conversation_members cm
    where cm.conversation_id = message_reports.conversation_id
      and cm.user_id = auth.uid()
  )
  and exists (
    select 1
    from public.messages m
    where m.id = message_reports.message_id
      and m.conversation_id = message_reports.conversation_id
  )
);

drop policy if exists "Users can read own message reports" on public.message_reports;
create policy "Users can read own message reports"
on public.message_reports
for select
to authenticated
using (reporter_id = auth.uid());
