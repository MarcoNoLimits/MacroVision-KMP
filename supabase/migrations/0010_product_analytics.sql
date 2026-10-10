-- ─────────────────────────────────────────────────────────────────────────────
-- 0010 — Product analytics: gateway-only ingestion, retention, dashboard views
--
-- Before this migration fitter.analytics_events had received 0 rows: the app
-- inserted columns (event_type, properties, occurred_at) the table doesn't have.
-- Events now arrive only through the analyze-meal gateway (POST /v1/events),
-- which verifies the JWT and calls fitter.log_events() as service_role. The
-- direct client insert policy is dropped so the table can't be spammed.
--
-- Payloads are behavioural only (counts, durations, screen names, error
-- classes). Meal names, calories and other nutrition values are never sent.
--
-- Retention: diagnostics 90 days, everything else 400 days (see privacy policy).
-- Dashboards live in the unexposed schema fitcal_analytics: query them from the
-- Supabase SQL editor. They are not reachable through the API.
-- ─────────────────────────────────────────────────────────────────────────────

alter table fitter.analytics_events
  add column if not exists session_id  text,
  add column if not exists app_version text,
  add column if not exists platform    text,
  add column if not exists client_ts   timestamptz,
  add column if not exists source      text not null default 'client';

create index if not exists analytics_events_event_time_idx on fitter.analytics_events (event, created_at desc);
create index if not exists analytics_events_time_idx on fitter.analytics_events (created_at);

-- Clients no longer insert directly; only the gateway (service_role) writes.
drop policy if exists "insert own events" on fitter.analytics_events;
revoke insert, update, delete on fitter.analytics_events from authenticated, anon;

-- ─── Ingestion ───────────────────────────────────────────────────────────────
-- p_events: [{ "event": "scan_succeeded", "props": {...}, "client_ts": 1760000000000,
--              "session_id": "…" }, …]
-- Invalid entries are skipped, never fatal. Returns the number of rows inserted.
create or replace function fitter.log_events(
  p_user_id     uuid,
  p_events      jsonb,
  p_app_version text default null,
  p_platform    text default null,
  p_source      text default 'client'
)
returns integer
language plpgsql
security definer
set search_path = fitter, public
as $$
declare
  v_inserted integer;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'log_events is restricted to service_role' using errcode = '42501';
  end if;
  if jsonb_typeof(p_events) is distinct from 'array' then
    return 0;
  end if;

  insert into fitter.analytics_events
    (user_id, event, payload, session_id, app_version, platform, client_ts, source)
  select
    p_user_id,
    e->>'event',
    case when jsonb_typeof(e->'props') = 'object' and length((e->'props')::text) <= 4096
         then e->'props' else '{}'::jsonb end,
    left(e->>'session_id', 64),
    left(p_app_version, 32),
    left(p_platform, 16),
    -- Device clocks drift; keep the client time only when it is plausible.
    case when (e->>'client_ts') ~ '^[0-9]{12,14}$'
              and to_timestamp((e->>'client_ts')::bigint / 1000.0)
                  between now() - interval '30 days' and now() + interval '1 day'
         then to_timestamp((e->>'client_ts')::bigint / 1000.0) end,
    case when p_source = 'server' then 'server' else 'client' end
  from (select value as e from jsonb_array_elements(p_events) limit 200) batch
  where (e->>'event') ~ '^[a-z][a-z0-9_]{1,63}$';

  get diagnostics v_inserted = row_count;

  -- Opportunistic retention, about once per hundred calls (no pg_cron on this project).
  if random() < 0.01 then
    delete from fitter.analytics_events
     where (event = 'diagnostic' and created_at < now() - interval '90 days')
        or created_at < now() - interval '400 days';
  end if;

  return v_inserted;
end;
$$;

revoke all on function fitter.log_events(uuid, jsonb, text, text, text) from public, anon, authenticated;
grant execute on function fitter.log_events(uuid, jsonb, text, text, text) to service_role;

-- ─── Dashboards (SQL editor only) ────────────────────────────────────────────
create schema if not exists fitcal_analytics;
revoke all on schema fitcal_analytics from public, anon, authenticated;
grant usage on schema fitcal_analytics to service_role;

-- Event time: client time when trustworthy, else server receipt time.
create or replace view fitcal_analytics.events as
select id, user_id, event, payload, session_id, app_version, platform, source,
       coalesce(client_ts, created_at) as ts, created_at
  from fitter.analytics_events;

-- One row per day: the numbers to check every morning.
create or replace view fitcal_analytics.daily_kpis as
with e as (select *, (ts at time zone 'utc')::date as day from fitcal_analytics.events),
first_seen as (select user_id, min(day) as day from e where user_id is not null group by user_id)
select
  e.day,
  count(distinct e.user_id)                                                   as active_users,
  (select count(*) from first_seen f where f.day = e.day)                     as new_users,
  count(*) filter (where event = 'scan_started')                              as scans_started,
  count(*) filter (where event = 'scan_succeeded')                            as scans_succeeded,
  count(*) filter (where event = 'scan_failed')                               as scans_failed,
  count(*) filter (where event = 'meal_logged')                               as meals_logged,
  count(*) filter (where event = 'meal_logged' and (payload->>'edited')::boolean) as meals_corrected,
  count(*) filter (where event = 'quota_exhausted')                           as quota_walls,
  count(*) filter (where event = 'ad_impression')                             as ad_impressions,
  round(sum((payload->>'revenue')::numeric) filter (where event = 'ad_revenue'), 4) as ad_revenue_usd,
  count(*) filter (where event = 'account_created')                           as accounts_created,
  count(*) filter (where event = 'diagnostic')                                as errors
from e
group by e.day
order by e.day desc;

-- Scan funnel per day, in users: opened camera → captured → AI answered → logged.
create or replace view fitcal_analytics.scan_funnel as
with e as (select *, (ts at time zone 'utc')::date as day from fitcal_analytics.events)
select
  day,
  count(distinct user_id) filter (where event = 'screen_view' and payload->>'screen' = 'camera') as opened_camera,
  count(distinct user_id) filter (where event = 'photo_captured')  as captured_photo,
  count(distinct user_id) filter (where event = 'scan_succeeded')  as got_result,
  count(distinct user_id) filter (where event = 'meal_logged')     as logged_meal,
  count(*) filter (where event = 'review_abandoned')               as reviews_abandoned,
  count(*) filter (where event = 'scan_cancelled')                 as scans_cancelled
from e
group by day
order by day desc;

-- AI provider health from the gateway's own events (authoritative, not client-reported).
create or replace view fitcal_analytics.provider_health as
select
  (created_at at time zone 'utc')::date                                  as day,
  event,
  payload->>'provider'                                                   as provider,
  count(*)                                                               as calls,
  count(*) filter (where (payload->>'ok')::boolean)                      as ok,
  count(*) filter (where (payload->>'fallback')::boolean)                as used_fallback,
  percentile_cont(0.5)  within group (order by (payload->>'latency_ms')::numeric) as p50_ms,
  percentile_cont(0.95) within group (order by (payload->>'latency_ms')::numeric) as p95_ms
from fitter.analytics_events
where source = 'server' and event in ('vlm_analyze', 'vlm_recalculate')
group by 1, 2, 3
order by 1 desc, 2, 3;

-- How much users correct the AI: the main signal for improving estimates.
create or replace view fitcal_analytics.ai_accuracy as
select
  (ts at time zone 'utc')::date                                     as day,
  count(*)                                                          as meals_logged,
  round(avg(((payload->>'edited')::boolean)::int) * 100, 1)         as pct_edited,
  round(avg((payload->>'items_removed')::numeric), 2)               as avg_items_removed,
  round(avg((payload->>'items_added')::numeric), 2)                 as avg_items_added,
  round(avg((payload->>'items_swapped')::numeric), 2)               as avg_items_swapped,
  round(avg((payload->>'weights_changed')::numeric), 2)             as avg_weights_changed,
  round(avg((payload->>'weight_change_pct')::numeric), 1)           as avg_weight_change_pct,
  round(avg((payload->>'review_seconds')::numeric), 1)              as avg_review_seconds
from fitcal_analytics.events
where event = 'meal_logged'
group by 1
order by 1 desc;

-- Weekly cohorts by first-seen week: % of users active N days later.
create or replace view fitcal_analytics.retention as
with days as (
  select distinct user_id, (ts at time zone 'utc')::date as day
    from fitcal_analytics.events where user_id is not null
),
cohort as (select user_id, min(day) as first_day from days group by user_id)
select
  date_trunc('week', c.first_day)::date                                       as cohort_week,
  count(distinct c.user_id)                                                    as users,
  round(100.0 * count(distinct d.user_id) filter (where d.day = c.first_day + 1)  / count(distinct c.user_id), 1) as d1_pct,
  round(100.0 * count(distinct d.user_id) filter (where d.day = c.first_day + 7)  / count(distinct c.user_id), 1) as d7_pct,
  round(100.0 * count(distinct d.user_id) filter (where d.day = c.first_day + 30) / count(distinct c.user_id), 1) as d30_pct
from cohort c
left join days d on d.user_id = c.user_id
group by 1
order by 1 desc;

-- Most frequent errors in the last 7 days.
create or replace view fitcal_analytics.top_errors as
select
  coalesce(payload->>'tag', event)                                as tag,
  left(coalesce(payload->>'message', payload->>'error', '?'), 160) as message,
  count(*)                                          as occurrences,
  count(distinct user_id)                           as users,
  max(created_at)                                   as last_seen
from fitter.analytics_events
where event in ('diagnostic', 'scan_failed') and created_at > now() - interval '7 days'
group by 1, 2
order by occurrences desc
limit 50;

revoke all on all tables in schema fitcal_analytics from public, anon, authenticated;
grant select on all tables in schema fitcal_analytics to service_role;
