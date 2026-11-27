create or replace function public.list_my_conversations()
returns table (
  id uuid,
  title text,
  type text,
  description text,
  avatar_url text,
  username text,
  is_public boolean,
  created_at timestamptz,
  created_by uuid,
  member_role text,
  is_member boolean
)
language sql
stable
security definer
set search_path = ''
as $function$
  select
    c.id,
    c.title,
    c.type,
    c.description,
    c.avatar_url,
    c.username,
    c.is_public,
    c.created_at,
    c.created_by,
    coalesce(cm.role, case when c.created_by = (select auth.uid()) then 'owner' end) as member_role,
    (cm.user_id is not null) as is_member
  from public.conversations c
  left join public.conversation_members cm
    on cm.conversation_id = c.id
   and cm.user_id = (select auth.uid())
  where (select auth.uid()) is not null
    and (
      cm.user_id is not null
      or c.created_by = (select auth.uid())
      or (c.is_public = true and c.type in ('group', 'channel'))
    )
  order by c.created_at desc
  limit 500;
$function$;

revoke all on function public.list_my_conversations() from public, anon;
grant execute on function public.list_my_conversations() to authenticated;
