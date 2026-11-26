-- Optional HTTPS mini-app URL for each bot.
-- Bot owners can update this field through the existing bots owner policy.
alter table public.bots
  add column if not exists web_app_url text;

comment on column public.bots.web_app_url is
  'Optional HTTPS URL for a bot mini app, launched inside a sandboxed IranGram frame.';
