create extension if not exists pgcrypto;

-- quota ledger (anti-abuse core; client ScanQuotaManager becomes a cache)
create table if not exists public.scan_quota (
  user_id uuid not null references auth.users(id) on delete cascade,
  day     date not null default current_date,
  used    int  not null default 0,
  bonus   int  not null default 0,
  primary key (user_id, day)
);

-- meals: one row per meal (replaces the logged_meals JSON blob)
create table if not exists public.meals (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references auth.users(id) on delete cascade default auth.uid(),
  name       text not null,
  calories   int  not null,
  protein_g  real not null,
  carbs_g    real not null,
  fat_g      real not null,
  eaten_at   timestamptz not null default now(),
  photo_url  text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index if not exists meals_user_date_idx on public.meals (user_id, eaten_at desc);

-- profile mirror of UserProfile
create table if not exists public.profiles (
  user_id           uuid primary key references auth.users(id) on delete cascade,
  weight            real,
  height            real,
  cal_goal          int,
  protein_goal      int,
  carbs_goal        int,
  fat_goal          int,
  age               int,
  gender            text,
  goal_type         text,
  default_plate_size real,
  updated_at        timestamptz not null default now()
);

-- water mirror of water_intake_<date>
create table if not exists public.water_intake (
  user_id   uuid not null references auth.users(id) on delete cascade,
  day       date not null,
  amount_ml int  not null default 0,
  primary key (user_id, day)
);

alter table public.meals         enable row level security;
alter table public.profiles      enable row level security;
alter table public.water_intake  enable row level security;
alter table public.scan_quota    enable row level security;

drop policy if exists "own meals" on public.meals;
create policy "own meals" on public.meals
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "own profile" on public.profiles;
create policy "own profile" on public.profiles
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "own water" on public.water_intake;
create policy "own water" on public.water_intake
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "own quota" on public.scan_quota;
create policy "own quota" on public.scan_quota
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

-- atomic quota consume: returns true if within allowance (3 free + bonus; week-1 users get 5 — handled by passing allowance)
create or replace function public.consume_scan(p_allowance int default 3)
returns boolean
language sql
security definer
set search_path = public
as $$
  insert into public.scan_quota (user_id, day, used)
  values (auth.uid(), current_date, 1)
  on conflict (user_id, day) do update set used = scan_quota.used + 1
  returning (select scan_quota.used <= p_allowance + scan_quota.bonus
             from public.scan_quota
             where user_id = auth.uid() and day = current_date);
$$;
revoke all on function public.consume_scan(int) from public;
grant execute on function public.consume_scan(int) to authenticated;

-- atomic bonus scan grant (server authority)
create or replace function public.grant_bonus_scan(p_amount int default 1)
returns int
language sql
security definer
set search_path = public
as $$
  insert into public.scan_quota (user_id, day, bonus)
  values (auth.uid(), current_date, p_amount)
  on conflict (user_id, day) do update set bonus = scan_quota.bonus + p_amount
  returning scan_quota.bonus;
$$;
revoke all on function public.grant_bonus_scan(int) from public;
grant execute on function public.grant_bonus_scan(int) to authenticated;

-- query current scan quota snapshot
create or replace function public.get_scan_quota(p_allowance int default 3)
returns jsonb
language sql
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'used', coalesce(sq.used, 0),
    'bonus', coalesce(sq.bonus, 0),
    'remaining', greatest(0, (p_allowance + coalesce(sq.bonus, 0)) - coalesce(sq.used, 0))
  )
  from (select 1) dummy
  left join public.scan_quota sq on sq.user_id = auth.uid() and sq.day = current_date;
$$;
revoke all on function public.get_scan_quota(int) from public;
grant execute on function public.get_scan_quota(int) to authenticated;
