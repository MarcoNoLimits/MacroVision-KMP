-- Migration: 0006_schema_fitcal.sql
-- Aliases and views for the FitCal schema rebranding.
-- Allows queries to both 'fitcal' and legacy 'fitter' schemas seamlessly.

create schema if not exists fitcal;

-- Grant schema usage
grant usage on schema fitcal to anon, authenticated, service_role;

-- Create views or tables in fitcal mapping to fitter if fitter exists
do $$
declare
  tbl text;
begin
  for tbl in select unnest(array['scan_quota', 'meals', 'profiles', 'water_intake', 'analytics_events', 'user_meta', 'entitlements'])
  loop
    if exists (select 1 from information_schema.tables where table_schema = 'fitter' and table_name = tbl)
       and not exists (select 1 from information_schema.tables where table_schema = 'fitcal' and table_name = tbl) then
      execute format('create or replace view fitcal.%I as select * from fitter.%I;', tbl, tbl);
      execute format('grant select, insert, update, delete on fitcal.%I to authenticated;', tbl);
      execute format('grant select on fitcal.%I to anon;', tbl);
      execute format('grant all privileges on fitcal.%I to service_role;', tbl);
    end if;
  end loop;
end $$;

-- RPC compatibility functions in fitcal schema
create or replace function fitcal.get_scan_quota(
  p_user_id uuid default auth.uid(),
  p_allowance int default 3
)
returns table (
  used int,
  bonus int,
  remaining int,
  allowed boolean
)
language plpgsql
security definer
set search_path = fitter, fitcal, public
as $$
begin
  return query select * from fitter.get_scan_quota(p_user_id, p_allowance);
end;
$$;

create or replace function fitcal.consume_scan(
  p_user_id uuid default auth.uid(),
  p_allowance int default 3
)
returns boolean
language plpgsql
security definer
set search_path = fitter, fitcal, public
as $$
begin
  return fitter.consume_scan(p_user_id, p_allowance);
end;
$$;

grant execute on function fitcal.get_scan_quota to authenticated, anon, service_role;
grant execute on function fitcal.consume_scan to authenticated, service_role;
