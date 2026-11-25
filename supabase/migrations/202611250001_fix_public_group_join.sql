-- Allow authenticated users to discover public groups and join public groups.
-- Private groups remain invisible and cannot be self-joined.
create or replace function private.can_insert_conversation_member(
  p_conversation_id uuid,
  p_user_id uuid,
  p_role text
)
returns boolean
language sql
stable
security definer
set search_path to ''
as $function$
  select case
    when p_user_id = (select auth.uid()) and p_role = 'owner' then
      exists (
        select 1
        from public.conversations c
        where c.id = p_conversation_id
          and c.created_by = (select auth.uid())
      )
    when p_user_id = (select auth.uid()) and p_role = 'member' then
      exists (
        select 1
        from public.conversations c
        where c.id = p_conversation_id
          and (
            c.type = 'channel'
            or (c.type = 'group' and c.is_public = true)
          )
      )
    when p_role in ('member', 'admin') then
      private.is_conversation_owner(p_conversation_id)
    else false
  end;
$function$;

drop policy if exists "Authenticated users can discover public channels"
  on public.conversations;

create policy "Authenticated users can discover public channels"
  on public.conversations
  for select
  to authenticated
  using (
    type in ('channel', 'group')
    and is_public = true
  );
