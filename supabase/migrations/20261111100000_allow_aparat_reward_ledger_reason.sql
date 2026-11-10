-- The reward review function records both YouTube and Aparat approvals.
-- Keep the ledger constraint aligned with the supported platforms.
alter table public.reward_ledger
  drop constraint if exists reward_ledger_reason_check;

alter table public.reward_ledger
  add constraint reward_ledger_reason_check
  check (reason = any (array[
    'youtube_subscription'::text,
    'aparat_follow'::text,
    'ad_redemption'::text
  ]));
