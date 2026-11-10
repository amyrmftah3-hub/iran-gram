-- Keep private channels discoverable only to their existing members.
-- The separate "Members can view their conversations" policy continues to
-- grant members access to channels they have already joined.
drop policy if exists "Authenticated users can discover public channels" on public.conversations;

create policy "Authenticated users can discover public channels"
on public.conversations
for select
to authenticated
using (type = 'channel' and is_public = true);
