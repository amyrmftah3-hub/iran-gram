begin;

revoke update on table public.conversation_members from authenticated;
grant update(role) on table public.conversation_members to authenticated;

drop policy if exists "Owners can update conversation member roles" on public.conversation_members;
create policy "Owners can update conversation member roles"
on public.conversation_members
for update
to authenticated
using (
  private.is_conversation_owner(conversation_id)
  and role <> 'owner'
)
with check (
  private.is_conversation_owner(conversation_id)
  and role in ('admin','member')
);

drop policy if exists "Only channel owners can send official channel messages" on public.messages;
create policy "Channel owners and admins can send channel messages"
on public.messages
for insert
to authenticated
with check (
  sender_id = (select auth.uid())
  and private.is_conversation_member(conversation_id)
  and (
    not exists (
      select 1 from public.conversations c
      where c.id = messages.conversation_id
        and c.type = 'channel'
    )
    or exists (
      select 1 from public.conversation_members cm
      where cm.conversation_id = messages.conversation_id
        and cm.user_id = (select auth.uid())
        and cm.role in ('owner','admin')
    )
  )
);

commit;
