alter table public.stories
  add constraint stories_expire_within_24_hours
  check (
    expires_at > created_at
    and expires_at <= created_at + interval '24 hours'
  );
