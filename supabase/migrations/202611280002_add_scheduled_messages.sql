create table if not exists public.scheduled_messages (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  body text not null check (char_length(btrim(body)) between 1 and 4000),
  send_at timestamptz not null,
  status text not null default 'pending' check (status in ('pending','sent','cancelled','failed')),
  sent_message_id uuid references public.messages(id) on delete set null,
  processed_at timestamptz,
  error_message text,
  created_at timestamptz not null default now()
);

create index if not exists scheduled_messages_due_idx on public.scheduled_messages(send_at) where status='pending';
create index if not exists scheduled_messages_owner_chat_idx on public.scheduled_messages(user_id,conversation_id,send_at desc);

alter table public.scheduled_messages enable row level security;
grant select, insert, delete on public.scheduled_messages to authenticated;

drop policy if exists "Users view their scheduled messages" on public.scheduled_messages;
create policy "Users view their scheduled messages" on public.scheduled_messages for select to authenticated
using (user_id = (select auth.uid()));

drop policy if exists "Members schedule their own messages" on public.scheduled_messages;
create policy "Members schedule their own messages" on public.scheduled_messages for insert to authenticated
with check (
  user_id = (select auth.uid()) and send_at > now() and send_at <= now() + interval '365 days'
  and exists (
    select 1 from public.conversations c
    join public.conversation_members cm on cm.conversation_id = c.id
    where c.id = scheduled_messages.conversation_id and cm.user_id = (select auth.uid())
      and (c.type <> 'channel' or cm.role in ('owner','admin'))
  )
);

drop policy if exists "Users cancel their pending scheduled messages" on public.scheduled_messages;
create policy "Users cancel their pending scheduled messages" on public.scheduled_messages for delete to authenticated
using (user_id = (select auth.uid()) and status = 'pending');

create or replace function private.publish_due_scheduled_messages()
returns void language plpgsql security definer set search_path to ''
as $function$
declare v_job record; v_message_id uuid; v_can_send boolean;
begin
  for v_job in
    select sm.id, sm.user_id, sm.conversation_id, sm.body
    from public.scheduled_messages sm
    where sm.status = 'pending' and sm.send_at <= now()
    order by sm.send_at for update skip locked limit 100
  loop
    select exists (
      select 1 from public.conversation_members cm
      join public.conversations c on c.id = cm.conversation_id
      where cm.conversation_id = v_job.conversation_id and cm.user_id = v_job.user_id
        and (c.type <> 'channel' or cm.role in ('owner','admin'))
    ) into v_can_send;

    if not v_can_send then
      update public.scheduled_messages set status='cancelled',processed_at=now(),error_message='NOT_A_MEMBER_OR_NOT_ALLOWED' where id=v_job.id;
      continue;
    end if;

    begin
      insert into public.messages(conversation_id,sender_id,body) values(v_job.conversation_id,v_job.user_id,v_job.body)
      returning id into v_message_id;
      update public.scheduled_messages set status='sent',sent_message_id=v_message_id,processed_at=now(),error_message=null where id=v_job.id;
    exception when others then
      update public.scheduled_messages set status='failed',processed_at=now(),error_message=left(sqlerrm,300) where id=v_job.id;
    end;
  end loop;
end;
$function$;

revoke all on function private.publish_due_scheduled_messages() from public, anon, authenticated;

select cron.schedule('iran-gram-send-scheduled-messages','* * * * *','select private.publish_due_scheduled_messages();');