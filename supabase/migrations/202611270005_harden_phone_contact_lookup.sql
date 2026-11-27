-- Harden phone contact discovery: require a saved phone number and deny direct access to rate-limit records.
drop policy if exists "No direct access to contact lookup limits" on public.phone_contact_lookup_limits;
create policy "No direct access to contact lookup limits"
on public.phone_contact_lookup_limits
for all
to authenticated
using (false)
with check (false);

create or replace function public.lookup_phone_contacts(p_phones text[])
returns table (
  matched_phone text,
  id uuid,
  display_name text,
  username text,
  avatar_url text
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_phones text[];
  v_count integer;
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  if not exists (
    select 1 from public.profiles own_profile
    where own_profile.id = auth.uid() and own_profile.phone is not null
  ) then
    raise exception 'Save your mobile number before matching contacts';
  end if;

  if coalesce(cardinality(p_phones), 0) > 500 then
    raise exception 'At most 500 contacts can be matched at once';
  end if;

  insert into public.phone_contact_lookup_limits(user_id, window_started_at, lookup_count)
  values (auth.uid(), now(), 1)
  on conflict (user_id) do update
    set window_started_at = case
          when public.phone_contact_lookup_limits.window_started_at < now() - interval '1 hour' then now()
          else public.phone_contact_lookup_limits.window_started_at
        end,
        lookup_count = case
          when public.phone_contact_lookup_limits.window_started_at < now() - interval '1 hour' then 1
          else public.phone_contact_lookup_limits.lookup_count + 1
        end
  returning lookup_count into v_count;

  if v_count > 10 then
    return;
  end if;

  select coalesce(array_agg(distinct normalized), array[]::text[])
    into v_phones
  from (
    select public.normalize_iran_gram_phone(x) as normalized
    from unnest(coalesce(p_phones, array[]::text[])) as input(x)
  ) normalized_input
  where length(normalized) between 7 and 15;

  if cardinality(v_phones) = 0 then
    return;
  end if;

  return query
    select distinct
      public.normalize_iran_gram_phone(p.phone) as matched_phone,
      p.id,
      p.display_name,
      p.username,
      p.avatar_url
    from public.profiles p
    where p.id <> auth.uid()
      and p.phone is not null
      and public.normalize_iran_gram_phone(p.phone) = any(v_phones)
    limit 500;
end;
$$;

revoke all on function public.lookup_phone_contacts(text[]) from public, anon;
grant execute on function public.lookup_phone_contacts(text[]) to authenticated;
