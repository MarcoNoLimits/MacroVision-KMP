-- ─────────────────────────────────────────────────────────────────────────────
-- 0012 — In-app user feedback
--
-- Two kinds of input, both submitted through the analyze-meal gateway
-- (POST /v1/feedback → fitcal.submit_feedback, service_role only):
--   • 'scan_accuracy' — thumbs up/down on an AI estimate (rating 5 / 1) with
--     optional reason chips in `context`. No free text, no food names.
--   • 'bug' | 'idea' | 'other' — free-text message from Settings → Send feedback,
--     with an optional reply email the user chose to give.
-- Erased with the account (delete_account_data, 0011) and after 24 months.
-- Read it from the SQL editor: select * from fitcal_analytics.feedback_inbox;
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists fitcal.feedback (
  id            bigint generated always as identity primary key,
  user_id       uuid references auth.users(id) on delete cascade,
  kind          text not null check (kind in ('scan_accuracy', 'bug', 'idea', 'other')),
  rating        smallint check (rating between 1 and 5),
  message       text check (length(message) <= 2000),
  contact_email text check (length(contact_email) <= 254),
  context       jsonb not null default '{}'::jsonb,
  app_version   text,
  platform      text,
  status        text not null default 'new' check (status in ('new', 'read', 'done', 'wontfix')),
  created_at    timestamptz not null default now()
);

create index if not exists feedback_created_idx on fitcal.feedback (created_at desc);
create index if not exists feedback_user_idx on fitcal.feedback (user_id, created_at desc);

-- No client policies: only the gateway (service_role) reads or writes.
alter table fitcal.feedback enable row level security;
revoke all on fitcal.feedback from anon, authenticated;
grant all on fitcal.feedback to service_role;

-- Returns the new id, or null when the user hit the daily limit (30 per day).
create or replace function fitcal.submit_feedback(
  p_user_id       uuid,
  p_kind          text,
  p_rating        smallint default null,
  p_message       text default null,
  p_contact_email text default null,
  p_context       jsonb default '{}'::jsonb,
  p_app_version   text default null,
  p_platform      text default null
)
returns bigint
language plpgsql
security definer
set search_path = fitcal, public
as $$
declare
  v_id bigint;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'submit_feedback is restricted to service_role' using errcode = '42501';
  end if;

  if (select count(*) from fitcal.feedback
       where user_id = p_user_id and created_at > now() - interval '1 day') >= 30 then
    return null;
  end if;

  insert into fitcal.feedback (user_id, kind, rating, message, contact_email, context, app_version, platform)
  values (
    p_user_id,
    p_kind,
    p_rating,
    nullif(left(trim(p_message), 2000), ''),
    nullif(left(trim(p_contact_email), 254), ''),
    case when jsonb_typeof(p_context) = 'object' and length(p_context::text) <= 2048
         then p_context else '{}'::jsonb end,
    left(p_app_version, 32),
    left(p_platform, 16)
  )
  returning id into v_id;

  -- Opportunistic retention (no pg_cron on this project).
  if random() < 0.02 then
    delete from fitcal.feedback where created_at < now() - interval '24 months';
  end if;

  return v_id;
end;
$$;

revoke all on function fitcal.submit_feedback(uuid, text, smallint, text, text, jsonb, text, text)
  from public, anon, authenticated;
grant execute on function fitcal.submit_feedback(uuid, text, smallint, text, text, jsonb, text, text)
  to service_role;

-- ─── Reading it ──────────────────────────────────────────────────────────────
create or replace view fitcal_analytics.feedback_inbox as
select id, created_at, kind, status, rating, message, contact_email, context, app_version, user_id
  from fitcal.feedback
 where kind <> 'scan_accuracy'
 order by created_at desc;

-- Thumbs up/down on AI estimates per day, with the most common complaint.
create or replace view fitcal_analytics.scan_ratings as
with r as (
  select (created_at at time zone 'utc')::date as day, rating, context
    from fitcal.feedback where kind = 'scan_accuracy'
),
reasons as (
  select day, reason,
         row_number() over (partition by day order by count(*) desc) as rn
    from r, jsonb_array_elements_text(coalesce(r.context->'reasons', '[]'::jsonb)) as reason
   group by day, reason
)
select
  r.day,
  count(*)                                                         as ratings,
  count(*) filter (where rating >= 4)                              as thumbs_up,
  count(*) filter (where rating <= 2)                              as thumbs_down,
  round(100.0 * count(*) filter (where rating >= 4) / count(*), 1) as pct_positive,
  (select reason from reasons where reasons.day = r.day and rn = 1) as top_reason
from r
group by r.day
order by r.day desc;

revoke all on fitcal_analytics.feedback_inbox, fitcal_analytics.scan_ratings from public, anon, authenticated;
grant select on fitcal_analytics.feedback_inbox, fitcal_analytics.scan_ratings to service_role;
