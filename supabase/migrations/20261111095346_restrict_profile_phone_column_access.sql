revoke all on table public.profiles from authenticated;

grant select (id, display_name, bio, avatar_url, created_at, username, about)
on table public.profiles to authenticated;

grant insert (id, display_name, bio, avatar_url, username, about)
on table public.profiles to authenticated;

grant update (display_name, bio, avatar_url, username, about)
on table public.profiles to authenticated;
