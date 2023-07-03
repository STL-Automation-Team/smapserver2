-- 
-- Apply upgrade patches to survey definitions database
--

-- Version 23.07
alter table forward add column periodic_time time;
alter table forward add column periodic_period text;
alter table forward add column periodic_day_of_week integer;
alter table forward add column periodic_day_of_month integer;
alter table forward add column periodic_month integer;		

create table periodic (
	last_checked_time time
);
ALTER TABLE periodic OWNER TO ws;

alter table forward drop constraint forward_s_id_fkey;
