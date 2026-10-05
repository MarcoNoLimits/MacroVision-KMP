-- Migration: 0007_signup_bonus_allowance.sql
-- Permanent accounts get +1 free scan/day, so the week-one allowance can reach 6
-- (5 + 1). Raise consume_scan's server-side ceiling from 5 to 6; everything else is
-- unchanged from 0005.

create or replace function fitter.consume_scan(
  p_user_id uuid default auth.uid(),
  p_allowance int default 3
)
returns boolean
language plpgsql
security definer
set search_path = fitter, public
as $$
declare
  v_target_user_id uuid;
  v_result boolean;
  v_effective_allowance int;
begin
  v_effective_allowance := least(greatest(coalesce(p_allowance, 0), 0), 6);

  if auth.role() = 'authenticated' then
    v_target_user_id := auth.uid();
  else
    v_target_user_id := coalesce(p_user_id, auth.uid());
  end if;

  if v_target_user_id is null then
    raise exception 'User ID must not be null';
  end if;

  insert into fitter.user_meta (user_id)
  values (v_target_user_id)
  on conflict (user_id) do nothing;

  insert into fitter.scan_quota (user_id, day, used)
  values (v_target_user_id, current_date, 1)
  on conflict (user_id, day) do update set used = fitter.scan_quota.used + 1;

  select (sq.used <= v_effective_allowance + sq.bonus)
  into v_result
  from fitter.scan_quota sq
  where sq.user_id = v_target_user_id and sq.day = current_date;

  return coalesce(v_result, true);
end;
$$;

revoke all on function fitter.consume_scan(uuid, int) from public, anon;
grant execute on function fitter.consume_scan(uuid, int) to authenticated, service_role;
