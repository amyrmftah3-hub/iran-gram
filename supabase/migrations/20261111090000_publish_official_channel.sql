-- The official IranGram channel must be discoverable to signed-in users.
-- Membership is still controlled by the existing auth trigger and RLS policies.
update public.conversations
set is_public = true
where id = 'e39f6339-049e-462a-8bbe-c9bfaff04278'::uuid
  and type = 'channel';
