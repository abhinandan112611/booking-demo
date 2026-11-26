-- ADDS synthetic data generator. Everything here is GENERATED DEMO DATA, not real trains, timetables,
-- fares or people. Station codes/names are the only real data (DataMeet 2016, CC0).
-- Additive and idempotent: every INSERT uses ON CONFLICT DO NOTHING; nothing is updated or deleted.
-- Rerun on a later day to extend the 62-day window: missing dates are added, older runs are kept.
-- Run as booking_app (addsdb_test first):
--   psql -v ON_ERROR_STOP=1 -U booking_app -h localhost -d addsdb_test -f sql\adds_generate.sql
\set ON_ERROR_STOP on
BEGIN;

-- GUARD: only the two ADDS databases, and only after the 182-station seed.
DO $$
BEGIN
  IF current_database() NOT IN ('addsdb', 'addsdb_test') THEN
    RAISE EXCEPTION 'adds_generate.sql refused: database "%" is not addsdb or addsdb_test', current_database();
  END IF;
  IF (SELECT count(*) FROM stations) <> 182 THEN
    RAISE EXCEPTION 'adds_generate.sql refused: expected 182 stations, found % (apply sql/stations_seed.sql first)',
                    (SELECT count(*) FROM stations);
  END IF;
END $$;

-- ===== 1. config_rules. Section 7 values (web check of 25 Nov 2026, not re-checked) carry needs_verify = true.
-- DEMO ASSUMPTION rows (my own values, not official rules) carry needs_verify = false. Existing keys are kept.
INSERT INTO config_rules (key, value, note, needs_verify) VALUES
 ('arp.days',                        '60',    '7.1 advance reservation period, journey date excluded - VERIFY', true),
 ('arp.open_time',                   '08:00', '7.1 general booking opens at this IST time on the opening day - VERIFY', true),
 ('chart.hours_before_departure',    '4',     '7.1 booking closes at chart preparation, before origin departure - VERIFY', true),
 ('general.max_passengers',          '6',     '7.1 per reservation - VERIFY', true),
 ('general.monthly_tickets',         '6',     '7.1 per user per calendar month - VERIFY', true),
 ('general.monthly_tickets_aadhaar', '12',    '7.1 per user per calendar month if aadhaar_verified (simulated) - VERIFY', true),
 ('tatkal.open_days_before',         '1',     '7.2 opens one day before the journey date - VERIFY', true),
 ('tatkal.open_time.ac',             '10:00', '7.2 AC classes 2A, 3A, CC, 3E (IST, DB clock) - VERIFY', true),
 ('tatkal.open_time.nonac',          '11:00', '7.2 non-AC classes SL, 2S (IST, DB clock) - VERIFY', true),
 ('tatkal.max_passengers',           '4',     '7.2 per reservation - VERIFY', true),
 ('tatkal.share',                    '0.30',  'DEMO ASSUMPTION: share of each coach held as Tatkal, rounded down; not official', false),
 ('tatkal.charge_pct.2S',            '0.10',  '7.2 Tatkal charge as share of base fare for 2S - VERIFY', true),
 ('tatkal.charge_pct.default',       '0.30',  '7.2 Tatkal charge as share of base fare for other classes - VERIFY', true),
 ('tatkal.charge_min.SL', '100', '7.2 - VERIFY', true), ('tatkal.charge_max.SL', '200', '7.2 - VERIFY', true),
 ('tatkal.charge_min.CC', '125', '7.2 - VERIFY', true), ('tatkal.charge_max.CC', '225', '7.2 - VERIFY', true),
 ('tatkal.charge_min.3A', '300', '7.2 - VERIFY', true), ('tatkal.charge_max.3A', '400', '7.2 - VERIFY', true),
 ('tatkal.charge_min.3E', '300', '7.2 - VERIFY', true), ('tatkal.charge_max.3E', '400', '7.2 - VERIFY', true),
 ('tatkal.charge_min.2A', '400', '7.2 - VERIFY', true), ('tatkal.charge_max.2A', '500', '7.2 - VERIFY', true),
 ('tatkal.charge_min.2S', '10',  '7.2 - VERIFY', true), ('tatkal.charge_max.2S', '15',  '7.2 - VERIFY', true),
 ('classes.ac',                      '1A,2A,3A,3E,CC', '7.3 classes that add GST - VERIFY', true),
 ('fare.gst_pct.ac',                 '0.05',  '7.3 GST on AC classes - VERIFY', true),
 ('fare.per_km.2S', '0.45', 'DEMO ASSUMPTION: not an official fare', false),
 ('fare.per_km.SL', '0.60', 'DEMO ASSUMPTION: not an official fare', false),
 ('fare.per_km.CC', '1.30', 'DEMO ASSUMPTION: not an official fare', false),
 ('fare.per_km.3E', '1.45', 'DEMO ASSUMPTION: not an official fare', false),
 ('fare.per_km.3A', '1.60', 'DEMO ASSUMPTION: not an official fare', false),
 ('fare.per_km.2A', '2.30', 'DEMO ASSUMPTION: not an official fare', false),
 ('fare.per_km.1A', '3.80', 'DEMO ASSUMPTION: not an official fare', false),
 ('fare.reservation_charge.2S', '15', 'DEMO ASSUMPTION: not an official charge', false),
 ('fare.reservation_charge.SL', '20', 'DEMO ASSUMPTION: not an official charge', false),
 ('fare.reservation_charge.CC', '40', 'DEMO ASSUMPTION: not an official charge', false),
 ('fare.reservation_charge.3E', '40', 'DEMO ASSUMPTION: not an official charge', false),
 ('fare.reservation_charge.3A', '40', 'DEMO ASSUMPTION: not an official charge', false),
 ('fare.reservation_charge.2A', '50', 'DEMO ASSUMPTION: not an official charge', false),
 ('fare.reservation_charge.1A', '60', 'DEMO ASSUMPTION: not an official charge', false),
 ('refund.flat.SL', '120', '7.4 flat clerkage per passenger (changed 1 Apr 2026 per news reports) - VERIFY', true),
 ('refund.flat.3A', '180', '7.4 - VERIFY', true),
 ('refund.flat.3E', '180', '7.4 - VERIFY', true),
 ('refund.flat.CC', '180', '7.4 - VERIFY', true),
 ('refund.flat.2A', '200', '7.4 - VERIFY', true),
 ('refund.flat.1A', '240', '7.4 - VERIFY', true),
 ('refund.flat.2S', '60',  '7.4 - VERIFY', true),
 ('refund.flat_until_hours', '72',   '7.4 more than this many hours before departure: flat amount only - VERIFY', true),
 ('refund.pct.72_24',        '0.25', '7.4 72-24 h: 25% of fare, not less than the flat amount - VERIFY', true),
 ('refund.pct.24_8',         '0.50', '7.4 24-8 h: 50% of fare, not less than the flat amount - VERIFY', true),
 ('refund.none_below_hours', '8',    '7.4 less than 8 h before departure: no refund - VERIFY', true),
 ('refund.tatkal',           '0',    '7.2/7.4 confirmed Tatkal cancellation refunds nothing - VERIFY', true)
ON CONFLICT (key) DO NOTHING;

-- ===== 2. Trains (synthetic names and numbers; letters in train_no so they cannot look like real 5-digit numbers)
INSERT INTO resources (name, train_no, train_type) VALUES
 ('ADDS Express 1','ADX01','OVERNIGHT'), ('ADDS Express 2','ADX02','OVERNIGHT'), ('ADDS Express 3','ADX03','OVERNIGHT'),
 ('ADDS Express 4','ADX04','OVERNIGHT'), ('ADDS Express 5','ADX05','OVERNIGHT'), ('ADDS Express 6','ADX06','OVERNIGHT'),
 ('ADDS Express 7','ADX07','OVERNIGHT'),
 ('ADDS Day Express 1','ADD01','DAY'), ('ADDS Day Express 2','ADD02','DAY'), ('ADDS Day Express 3','ADD03','DAY')
ON CONFLICT (train_no) DO NOTHING;

-- ===== 3. Stops (real station codes; times, day offsets and distances are SYNTHETIC approximations)
INSERT INTO train_stops (train_id, stop_seq, station_id, arr_time, dep_time, day_offset, distance_km)
SELECT r.id, v.seq, s.id, v.arr::time, v.dep::time, v.d, v.km
FROM (VALUES
 ('ADX01',1,'NDLS',NULL,'20:00',0,0),  ('ADX01',2,'CNB','01:30','01:40',1,440), ('ADX01',3,'ALD','04:00','04:10',1,635),
 ('ADX01',4,'MGS','06:30','06:45',1,785), ('ADX01',5,'PNBE','10:00',NULL,1,1000),
 ('ADX02',1,'NDLS',NULL,'21:30',0,0),  ('ADX02',2,'AGC','00:30','00:35',1,195), ('ADX02',3,'GWL','02:15','02:20',1,315),
 ('ADX02',4,'JHS','03:40','03:50',1,415), ('ADX02',5,'BPL','08:00',NULL,1,705),
 ('ADX03',1,'CSTM',NULL,'19:00',0,0),  ('ADX03',2,'KYN','19:55','20:00',0,54),  ('ADX03',3,'NK','22:40','22:45',0,187),
 ('ADX03',4,'BSL','02:30','02:40',1,445), ('ADX03',5,'NGP','08:30',NULL,1,835),
 ('ADX04',1,'MAS',NULL,'22:00',0,0),   ('ADX04',2,'KPD','23:50','23:55',0,130), ('ADX04',3,'JTJ','01:20','01:25',1,215),
 ('ADX04',4,'SBC','05:00',NULL,1,360),
 ('ADX05',1,'HWH',NULL,'21:00',0,0),   ('ADX05',2,'BWN','22:15','22:20',0,95),  ('ADX05',3,'ASN','23:30','23:35',0,200),
 ('ADX05',4,'DHN','00:50','00:55',1,260), ('ADX05',5,'GAYA','03:30','03:35',1,460), ('ADX05',6,'MGS','06:00',NULL,1,665),
 ('ADX06',1,'SC',NULL,'18:30',0,0),    ('ADX06',2,'KZJ','20:15','20:20',0,132), ('ADX06',3,'BZA','00:30','00:40',1,350),
 ('ADX06',4,'RJY','03:00','03:05',1,500), ('ADX06',5,'VSKP','07:00',NULL,1,700),
 ('ADX07',1,'ADI',NULL,'22:30',0,0),   ('ADX07',2,'BRC','00:20','00:25',1,100), ('ADX07',3,'ST','02:10','02:15',1,230),
 ('ADX07',4,'BVI','05:30','05:35',1,465), ('ADX07',5,'BCT','06:30',NULL,1,490),
 ('ADD01',1,'NDLS',NULL,'07:00',0,0),  ('ADD01',2,'UMB','10:00','10:05',0,200), ('ADD01',3,'CDG','11:00',NULL,0,245),
 ('ADD02',1,'CSTM',NULL,'06:00',0,0),  ('ADD02',2,'DR','06:15','06:17',0,9),    ('ADD02',3,'TNA','06:40','06:42',0,34),
 ('ADD02',4,'KYN','07:00','07:02',0,54), ('ADD02',5,'PUNE','09:30',NULL,0,192),
 ('ADD03',1,'HWH',NULL,'06:00',0,0),   ('ADD03',2,'KGP','07:45','07:50',0,116), ('ADD03',3,'CTC','11:30','11:35',0,410),
 ('ADD03',4,'BBS','12:15',NULL,0,437)
) AS v(train_no, seq, code, arr, dep, d, km)
JOIN resources r ON r.train_no = v.train_no
JOIN stations  s ON s.code = v.code
ON CONFLICT (train_id, stop_seq) DO NOTHING;

DO $$ BEGIN
  IF (SELECT count(*) FROM train_stops ts JOIN resources r ON r.id = ts.train_id WHERE r.train_no ~ '^AD[XD][0-9]{2}$') <> 47 THEN
    RAISE EXCEPTION 'adds_generate.sql: expected 47 train stops (a station code did not match)';
  END IF;
END $$;

-- ===== 4. Runs: one per train per day, today .. today+61 (DB date, Asia/Kolkata)
INSERT INTO train_runs (train_id, run_date)
SELECT r.id, d::date
FROM resources r
CROSS JOIN generate_series(current_date, current_date + 61, interval '1 day') AS d
WHERE r.train_no ~ '^AD[XD][0-9]{2}$'
ON CONFLICT (train_id, run_date) DO NOTHING;

-- ===== 5. Class plan per train type (berth layouts from Section 6; 2S, 3E, 1A layouts are demo assumptions)
CREATE TEMP TABLE gen_class_plan (train_type text, class_code text, coaches int, per_coach int,
                                  coach_prefix text, pattern text[]) ON COMMIT DROP;
INSERT INTO gen_class_plan VALUES
 ('OVERNIGHT','SL',6,72,'S','{LB,MB,UB,LB,MB,UB,SL,SU}'),
 ('OVERNIGHT','3A',3,64,'B','{LB,MB,UB,LB,MB,UB,SL,SU}'),
 ('OVERNIGHT','2A',1,48,'A','{LB,UB,LB,UB,SL,SU}'),
 ('OVERNIGHT','3E',1,80,'M','{LB,MB,UB,LB,MB,UB,SL,SU}'),
 ('OVERNIGHT','1A',1,24,'H','{LB,UB}'),
 ('DAY','CC',4,78,'C','{WS,MS,AS,AS,WS}'),
 ('DAY','2S',3,108,'D','{WS,MS,AS,AS,MS,WS}');

INSERT INTO run_classes (run_id, class_code, coaches, general_berths, tatkal_berths)
SELECT tr.id, p.class_code, p.coaches, p.coaches * (p.per_coach - t.n), p.coaches * t.n
FROM train_runs tr
JOIN resources r       ON r.id = tr.train_id
JOIN gen_class_plan p  ON p.train_type = r.train_type
CROSS JOIN LATERAL (SELECT CASE WHEN p.class_code = '1A' THEN 0
       ELSE floor(p.per_coach * (SELECT value::numeric FROM config_rules WHERE key = 'tatkal.share'))::int END AS n) t
ON CONFLICT (run_id, class_code) DO NOTHING;

-- ===== 6. Berths: one row per berth; the highest berth numbers of each coach are TATKAL
INSERT INTO slots (resource_id, start_time, end_time, status, run_id, class_code, coach_no, berth_no, berth_type, quota)
SELECT tr.train_id,
       tr.run_date + o.dep_time,
       (tr.run_date + f.day_offset) + f.arr_time,
       'AVAILABLE', tr.id, rc.class_code,
       p.coach_prefix || c.n, b.n,
       p.pattern[((b.n - 1) % array_length(p.pattern, 1)) + 1],
       CASE WHEN b.n > p.per_coach - rc.tatkal_berths / rc.coaches THEN 'TATKAL' ELSE 'GENERAL' END
FROM train_runs tr
JOIN resources r      ON r.id = tr.train_id
JOIN run_classes rc   ON rc.run_id = tr.id
JOIN gen_class_plan p ON p.train_type = r.train_type AND p.class_code = rc.class_code
JOIN train_stops o    ON o.train_id = tr.train_id AND o.stop_seq = 1
JOIN train_stops f    ON f.train_id = tr.train_id
                     AND f.stop_seq = (SELECT max(stop_seq) FROM train_stops WHERE train_id = tr.train_id)
CROSS JOIN LATERAL generate_series(1, rc.coaches)  AS c(n)
CROSS JOIN LATERAL generate_series(1, p.per_coach) AS b(n)
WHERE NOT EXISTS (SELECT 1 FROM slots x WHERE x.run_id = tr.id AND x.class_code = rc.class_code)  -- refresh touches only new runs
ON CONFLICT (run_id, class_code, coach_no, berth_no) DO NOTHING;

-- ===== 7. Users (all synthetic). demo1..demo5: documented dummy password "adds-demo-2026" (BCrypt hash only).
INSERT INTO users (name, email, password_hash, aadhaar_verified)
SELECT 'Demo Account ' || g, 'demo' || g || '@adds-demo.invalid',
       '$2a$10$ywj5DhORf4aBzDHdyTXGK.XtuLqVHYDMf9USfL7e8WV.AvEYC8Qbi', false   -- BCrypt cost 10 of the documented demo password
FROM generate_series(1, 5) AS g
ON CONFLICT (lower(email)) WHERE email IS NOT NULL DO NOTHING;

-- 10,000 load-test users: no usable password (NULL hash, cannot log in), simulated Aadhaar flag set.
INSERT INTO users (name, email, password_hash, aadhaar_verified)
SELECT 'Demo User ' || lpad(g::text, 5, '0'), 'user' || lpad(g::text, 5, '0') || '@adds-demo.invalid', NULL, true
FROM generate_series(1, 10000) AS g
ON CONFLICT (lower(email)) WHERE email IS NOT NULL DO NOTHING;

COMMIT;
