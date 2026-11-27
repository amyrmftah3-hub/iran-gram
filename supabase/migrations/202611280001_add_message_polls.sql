create table if not exists public.message_polls (
  message_id uuid primary key references public.messages(id) on delete cascade,
  created_by uuid not null references auth.users(id) on delete cascade,
  question text not null check (char_length(btrim(question)) between 1 and 240),
  options jsonb not null check (jsonb_typeof(options) = 'array' and jsonb_array_length(options) between 2 and 10),
  closed_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.message_poll_votes (
  message_id uuid not null references public.message_polls(message_id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  option_index integer not null check (option_index >= 0 and option_index < 10),
  created_at timestamptz not null default now(),
  primary key (message_id, user_id)
);

create index if not exists message_poll_votes_message_idx on public.message_poll_votes(message_id);

alter table public.message_polls enable row level security;
alter table public.message_poll_votes enable row level security;

grant select, insert, update, delete on public.message_polls to authenticated;
grant select, insert, update, delete on public.message_poll_votes to authenticated;

create policy "Members can view message polls" on public.message_polls for select to authenticated
using (exists (
  select 1 from public.messages m
  join public.conversation_members cm on cm.conversation_id = m.conversation_id
  where m.id = message_polls.message_id and cm.user_id = (select auth.uid())
));

create policy "Message authors can create polls" on public.message_polls for insert to authenticated
with check (created_by = (select auth.uid()) and exists (
  select 1 from public.messages m
  join public.conversation_members cm on cm.conversation_id = m.conversation_id
  where m.id = message_polls.message_id and m.sender_id = (select auth.uid()) and cm.user_id = (select auth.uid())
));

create policy "Poll creators can update polls" on public.message_polls for update to authenticated
using (created_by = (select auth.uid())) with check (created_by = (select auth.uid()));

create policy "Poll creators can delete polls" on public.message_polls for delete to authenticated
using (created_by = (select auth.uid()));

create policy "Members can view poll votes" on public.message_poll_votes for select to authenticated
using (exists (
  select 1 from public.messages m
  join public.conversation_members cm on cm.conversation_id = m.conversation_id
  where m.id = message_poll_votes.message_id and cm.user_id = (select auth.uid())
));

create policy "Members can vote in open polls" on public.message_poll_votes for insert to authenticated
with check (user_id = (select auth.uid()) and exists (
  select 1 from public.message_polls p
  join public.messages m on m.id = p.message_id
  join public.conversation_members cm on cm.conversation_id = m.conversation_id
  where p.message_id = message_poll_votes.message_id and p.closed_at is null and cm.user_id = (select auth.uid())
));

create policy "Users can change their poll vote" on public.message_poll_votes for update to authenticated
using (user_id = (select auth.uid())) with check (
  user_id = (select auth.uid()) and exists (
    select 1 from public.message_polls p
    join public.messages m on m.id = p.message_id
    join public.conversation_members cm on cm.conversation_id = m.conversation_id
    where p.message_id = message_poll_votes.message_id and p.closed_at is null and cm.user_id = (select auth.uid())
  )
);

create policy "Users can remove their poll vote" on public.message_poll_votes for delete to authenticated
using (user_id = (select auth.uid()));

create or replace function private.validate_message_poll_vote()
returns trigger language plpgsql set search_path to ''
as $function$
declare v_option_count integer; v_closed_at timestamptz;
begin
  select jsonb_array_length(p.options), p.closed_at into v_option_count, v_closed_at
  from public.message_polls p where p.message_id = new.message_id;
  if not found then raise exception 'POLL_NOT_FOUND'; end if;
  if v_closed_at is not null then raise exception 'POLL_CLOSED'; end if;
  if new.option_index < 0 or new.option_index >= v_option_count then raise exception 'INVALID_POLL_OPTION'; end if;
  return new;
end;
$function$;

drop trigger if exists validate_message_poll_vote on public.message_poll_votes;
create trigger validate_message_poll_vote before insert or update of message_id, option_index
on public.message_poll_votes for each row execute function private.validate_message_poll_vote();