-- Migration: 0006_schema_fitcal.sql
-- Exposes the FitCal-branded `fitcal` schema as a thin alias over `fitter`.
-- The app's PostgREST client and the Worker target `fitcal`; data stays in `fitter`.
--
-- Views use security_invoker so the caller's RLS applies. Without it they run as
-- their owner (`postgres`, which has BYPASSRLS) and would expose every user's rows.
-- Function wrappers are SECURITY INVOKER and delegate to the hardened `fitter`
-- functions from 0005, so they add no privilege of their own.

create schema if not exists fitcal;

revoke all on schema fitcal from public, anon;
grant usage on schema fitcal to authenticated, service_role;

do $$
declare
  tbl text;
begin
  foreach tbl in array array['scan_quota', 'meals', 'profiles', 'water_intake', 'analytics_events', 'user_meta']
  loop
    if exists (select 1 from information_schema.tables where table_schema = 'fitter' and table_name = tbl) then
      execute format('create or replace view fitcal.%I with (security_invoker = true) as select * from fitter.%I', tbl, tbl);
      execute format('revoke all on fitcal.%I from public, anon', tbl);
      execute format('grant select, insert, update, delete on fitcal.%I to authenticated', tbl);
      execute format('grant all on fitcal.%I to service_role', tbl);
    end if;
  end loop;
end $$;

create or replace function fitcal.consume_scan(
  p_user_id uuid default auth.uid(),
  p_allowance int default 3
)
returns boolean
language sql
security invoker
set search_path = ''
as $$
  select fitter.consume_scan(p_user_id, p_allowance);
$$;

create or replace function fitcal.get_scan_quota(
  p_allowance int default 3,
  p_user_id uuid default auth.uid()
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$
  select fitter.get_scan_quota(p_allowance, p_user_id);
$$;

create or replace function fitcal.grant_bonus_scan(
  p_amount int default 1,
  p_user_id uuid default auth.uid()
)
returns int
language sql
security invoker
set search_path = ''
as $$
  select fitter.grant_bonus_scan(p_amount, p_user_id);
$$;

revoke all on function fitcal.consume_scan(uuid, int) from public, anon;
grant execute on function fitcal.consume_scan(uuid, int) to authenticated, service_role;

revoke all on function fitcal.get_scan_quota(int, uuid) from public, anon;
grant execute on function fitcal.get_scan_quota(int, uuid) to authenticated, service_role;

revoke all on function fitcal.grant_bonus_scan(int, uuid) from public, anon, authenticated;
grant execute on function fitcal.grant_bonus_scan(int, uuid) to service_role;
