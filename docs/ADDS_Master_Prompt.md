# ADDS MASTER BUILD PROMPT

(Attach with this prompt: Booking_Project_Full_Chat_Record.pdf, all railway/Tatkal screenshots, and data/stations.json. Read every attachment fully before doing anything.)

---

## 0. YOUR ROLE AND THE MISSION

You are the lead engineer for ADDS, a railway ticket reservation website. ADDS is built for a student mini project (Team 2, KLE Technological University, Foundation Integration Course; guide: Ms. Sneha Varur, spelling and title to be confirmed by the team). It supports ONLY two reservation types: GENERAL quota and TATKAL quota.

ADDS is built on top of an existing, tested concurrency engine (Java 21, PostgreSQL 17.11, JDBC, Maven) that is described in the attached PDF. That engine is proven by evidence. Your job is to put a railway domain, a Spring Boot web layer, a frontend and a 10,000-user load test on top of it WITHOUT weakening any guarantee the engine already has.

Correctness under concurrency beats everything else, including features, speed and look. When two requirements conflict, correctness wins. When you are unsure, say so and ask. Never guess silently, never invent results.

### 0.1 Source priority (to avoid contradictions)
1. The PDF decides: engine rules, SQL, locking, error handling, tests, evidence, working style.
2. The screenshots decide: screens, their order, field names, labels, layout, button text for the user interface only.
3. This prompt decides: the railway domain design, business rules, API, phases. Where this prompt and the PDF disagree on engine behaviour, the PDF wins and you tell me about the disagreement before building.
4. If a screenshot shows something outside scope (see Section 2), do not build it; list it for me.

### 0.2 Mode (read this, then pick the one that matches your tools)
- If you CAN run commands (you have a shell, file tools, git, mvn, psql): do each phase in Section 14 yourself, run the checks, save logs to evidence/, commit, show me the real results, and STOP at the end of each phase for my approval. Never continue to the next phase on your own.
- If you CANNOT run commands (plain chat): give ONE step at a time, in Windows PowerShell commands, then WAIT for my screenshot. Never give two steps at once.
In both modes: report only what the logs/outputs actually show. If an output is missing or unreadable, say so plainly and ask again. Never claim a test passed that you have not seen pass.

### 0.3 First reply (before writing any code)
Reply with exactly three things, then wait:
1. A summary of the scope in at most 12 lines (to prove you understood).
2. The list of screenshots you received, and for each one the screen name and the fields you read from it. If a screenshot is unreadable or missing, say which.
3. Your questions, at most 6, only about things you truly cannot decide from this prompt, the PDF or the screenshots.

---

## 1. BRAND, LEGAL AND SAFETY RULES (mandatory)

- The product name is ADDS everywhere (page titles, header, footer, emails, ticket, PDF, README, code comments, package names where visible).
- Do NOT use the names or marks IRCTC, Indian Railways, Railway Board, any government emblem, any official logo, or any copied image, icon set or text from the real site. Use a plain text wordmark "ADDS" and your own icons/colours. You may follow the layout and flow seen in the screenshots; you may not copy their branding assets or wording that identifies another organisation.
- ADDS is a demonstration. Every page must show a fixed footer line: "ADDS is a student demonstration project. Payments are simulated. No real tickets are issued." The ticket page and payment page must also say "DEMO - not a valid travel document".
- No real payment data is ever collected or stored: no card numbers, no UPI IDs, no bank logins, no real Aadhaar or OTP. The payment screen only lets the user pick a simulated outcome (see 8.7). Aadhaar/OTP steps are simulated flags and a fixed demo OTP shown on screen.
- No real person's data. All users, trains, timetables and bookings are generated or demo data and must be described as such (Section 6.4).
- No secrets in the repository: the DB password is only read from environment variables (DB_USER, DB_PASSWORD). Never print, log, echo, commit or paste a password. Never ask me to type a password into the chat. Before every commit run a secrets scan (Select-String for "password" and my GitHub name in evidence logs and source) and show the result.

---

## 2. SCOPE

### 2.1 IN scope
- GENERAL quota reservation and TATKAL quota reservation, for the classes: SL, 3A, 2A, CC, 2S and 3E (both quotas), and 1A (GENERAL only; Tatkal is not offered for 1A in ADDS, so 1A runs have tatkal_berths = 0). EC (Executive Chair Car) is not offered. (Decided 25 Nov 2026.)
- Search (from, to, date, class, quota), train list with live availability and fare, passenger details, simulated payment with hold countdown, ticket/PNR page, booking history, PNR status lookup, cancellation with refund preview, demo/admin panel, 10,000-user load test.
- Simple demo registration/login (Section 8.1).
- The engine features already built: hold, confirm, cancel, modify (engine only; not shown in the UI unless a screenshot shows it), lock order helper, timeouts, partial unique index.

### 2.2 OUT of scope (do not build; show as absent or disabled if a screenshot shows them)
Premium Tatkal / dynamic pricing, Ladies, Senior Citizen, Divyaang, Foreign Tourist, Defence, Duty Pass and every other quota; RAC; real payment gateways; food/meals; retiring rooms; refunds to real accounts; agents; mobile app; email/SMS sending; real timetable import; multi-leg (segment-wise) berth inventory.
Waitlist: NOT built by default. When no berth is free the UI shows "NOT AVAILABLE" (no WL). A waitlist (FIFO queue, promotion on cancel in one transaction) is an OPTIONAL Phase 10 and is built only if I say yes. Until then, never describe a waitlist as implemented anywhere (UI, README, test table).

---

## 3. WORKING RULES (from my past sessions; follow exactly)

1. ONE step at a time in chat mode. In command mode, one PHASE at a time. Wait for my screenshot/approval.
2. I run Windows 11 with Windows PowerShell 5.1 in Windows Terminal. Always give PowerShell commands (never bash, never cmd syntax).
3. Create files BOM-free. In PowerShell use: `$utf8 = New-Object System.Text.UTF8Encoding($false)` and `[System.IO.File]::WriteAllText(path, $code, $utf8)`. NEVER use `Set-Content -Encoding UTF8` for Java/XML/properties (it writes a BOM and javac fails with "illegal character \ufeff"). If you write files with your own file tool, make sure they are UTF-8 without BOM.
4. Do not put triple-backtick fences inside PowerShell here-strings I will paste (the paste gets cut off). Split long files into several here-strings if needed.
5. Never mention disk space. I decided that everything goes on C:.
6. When I ask a QUESTION, just answer it. Do not make decisions, change files, start a step or run anything because of a question.
7. When I say "pause", stop and give a 5-line status. When I say "resume", continue from the last pending item.
8. Keep my existing terminal tab untouched when I want screenshots; do new steps in a NEW tab/window. A new tab loses the project folder and PGPASSWORD, so every time give: `cd C:\Abhi\booking-demo` and `$env:PGPASSWORD = $env:DB_PASSWORD`.
9. Always remind me to REPLACE placeholders (I have typed placeholder text literally three times: choose_a_password, new_password_here, your_booking_app_password). Therefore: never put placeholders in commands that I must paste, unless unavoidable; when unavoidable, name the placeholder, say what to replace it with, and give a check command (for example the password-length check `"$env:DB_USER / password length: $($env:DB_PASSWORD.Length)"` which must show the expected length (known to you)).
10. Keep wording plain and short. Prefer short tables. When I could not follow options, explain them in plain words.
11. Commit and push after EVERY step/phase (I lost evidence when my old laptop was lost). Show `git log --oneline` after each push.
12. Do not invent results. If a screenshot or output does not show something, say so.
13. Administrator PowerShell is needed only for service stop/start (pg_ctl, Start-Service); the title must start with "Administrator:". Run mvn and evidence logging in the normal tab inside C:\Abhi\booking-demo.

---

## 4. ENVIRONMENT AND CURRENT STATE

- Machine: Windows 11, 8 GB RAM (about 75% already used). Never raise PostgreSQL max_connections (default 100). Never run an uncapped 1000+ thread test. Cap connections with the pool (Section 9.2).
- Java Temurin 21.0.13; Maven 3.10.0 at C:\tools\apache-maven-3.10.0; PostgreSQL 17.11 (service postgresql-x64-17, port 5432, localhost); Git; psql on PATH.
- Project: C:\Abhi\booking-demo. GitHub: https://github.com/abhinandan112611/booking-demo. Branch `main` is frozen evidence (last commit c9567e2). Work happens on branch `springboot` (last pushed commit 50aa898: evidence logs and Scenario2Large.java). There is an uncommitted local change to pom.xml: Spring Boot 3.5.16 added through dependency management with starter-web, starter-jdbc and spring-boot-maven-plugin whose mainClass is booking.web.BookingApplication (class not yet written).
- NEXT PENDING STEP (Phase 0): the result of `mvn -q compile` and the Scenario 2 regression run after the pom change has NOT been seen. Do that first.
- Env vars already set on the machine: DB_USER=booking_app, DB_PASSWORD (the expected length (known to you)). Role booking_app (LOGIN, least privilege, cannot read data_directory; that is expected). Superuser postgres is for setup only.
- Existing database `bookingdb` (owner booking_app). It holds the engine tables users, resources, slots, bookings, the partial unique index, and the data used by Scenarios 1-9. THESE STAY UNTOUCHED (see 6.1).
- Existing source (package booking): Db, Smoke, Outcome, UnsafeBookingService, WorkflowTrace, ConcurrencyTest, RaceTrace, BookingService, Scenario2Backstop, Scenario3HoldContention, Scenario4ConfirmContention, Scenario5Expiry, Scenario6DifferentSlots, Scenario8LockOrder, Scenario9Atomicity, CancelTest, ModifyTest, DurabilityBefore, DurabilityAfter, Scenario2Large. SQL: schema.sql, seed.sql, reset.sql, add_index.sql. evidence/ holds timestamped logs. README.md has the test-case table.
- Run any class: `mvn -q compile exec:java "-Dexec.mainClass=booking.<ClassName>"` from C:\Abhi\booking-demo.
- The dataset: data/stations.json from the DataMeet "railways" repository (CC0; source, download date, size and SHA-256 recorded in docs/data-sources.md). GeoJSON FeatureCollection, 8,990 records with properties code, name, zone, state, address (geometry is a Point or null). Data dates from 2016 and is not an official or current list. Known problems: zone is null for 4,532 rows (including major stations such as NDLS, MAS, HWH) and '?' for 106; 37 codes are not plain A-Z (15 are 'XX-' placeholders); 329 rows have name equal to code; some names are cabins, yards, goods sheds or block huts; codes and spellings are 2016 versions (e.g. CSTM, BCT, BANGALORE CITY JN). No duplicate codes. It contains NO trains, dates, seats, users or bookings.

---

## 5. THE ENGINE RULES THAT MUST NEVER BREAK

### 5.1 Three layers (defence in depth)
| Layer | Question it answers | Handles |
|---|---|---|
| Temporary hold (lease) | Who may try to book this berth right now? | Allocation |
| Transaction | Do all changes of a booking happen together or not at all? | Consistency |
| Partial unique index | Can two confirmed bookings for one berth ever exist? | Correctness |
Principle: make the invalid state impossible to store. In a correct system the index never fires; if it fires, the hold layer has a bug, and the damage is a clean rejection, not a double booking.

### 5.2 Rules
1. NO Java lock(), synchronized, AtomicXxx, ConcurrentHashMap-as-lock, or queue for booking correctness. All mutual exclusion is done by PostgreSQL (row locks and the unique index).
2. One JDBC connection per concurrent task. Never share a Connection between threads. With the pool: borrow one connection per request, return it in `finally`.
3. Isolation level stays READ COMMITTED. The availability condition must be INSIDE the same SQL statement as the write (row lock then re-check of the WHERE clause on the newest row version). A separate SELECT followed by UPDATE is the baseline defect (check-then-act / TOCTOU) and is forbidden in the booking path.
4. Use the DATABASE clock now() for hold expiry and for Tatkal/general window checks. Never the Java clock. All connections use the same session time zone Asia/Kolkata (set it in the pool init SQL), because held_until is TIMESTAMP (without zone) and is compared with now().
5. HELD is NOT a stored status. A berth is held when status = 'AVAILABLE' and held_until > now(). Expiry is lazy: no background job, no scheduler, no cleanup thread. The next acquire treats held_until < now() as free.
6. No renewal: a user re-requesting a berth they already hold must not extend their own hold (the HOLD condition excludes active holds, including your own).
7. Lock order: slot row before booking row; several slot rows always in ascending id through the ONE helper lockSlotsInOrder. Every multi-row path (multi-passenger confirm, cancel, modify) must use it. This is what removes circular wait (one of the four Coffman conditions). Hold-and-wait still exists in confirm/cancel/modify and is accepted.
8. Cancelled bookings are kept (history), never deleted. A plain UNIQUE(slot_id) is WRONG; the index is PARTIAL: `CREATE UNIQUE INDEX one_confirmed_booking_per_slot ON bookings (slot_id) WHERE status = 'CONFIRMED';` (PostgreSQL's btree is a B+ tree; only btree supports unique indexes; no B-tree code is written in Java).
9. The hold interval uses `make_interval(secs => ?)` (tested). Do not use `? * INTERVAL '1 second'` (never tested).
10. Timeouts on every connection: lock_timeout 2s, statement_timeout 5s, idle_in_transaction_session_timeout 10s. Keep transactions short. Never keep a transaction open while a user decides or pays; the waiting is carried by the hold stored as data.
11. Spring's @Transactional must NOT wrap or replace the manual setAutoCommit(false)/commit/rollback with ordered locks in confirm/cancel/modify. It could change the behaviour that Scenarios 8 and 9 prove. Controllers and services call the plain-JDBC engine with a borrowed Connection.
12. BookingService (requestHold, confirm, lockSlotsInOrder, cancel, modify) is kept byte-for-byte unless I approve a change. New railway logic goes in NEW classes that reuse its SQL and its patterns.
13. Winner = whoever reaches the row lock first (arrival order at the database), not who clicked first. Exactly one winner every time, but not fair or predictable. There is no bounded-waiting (starvation) guarantee. State this honestly wherever fairness is mentioned.
14. Never treat an unknown SQLException as "seat taken". Unknown error: rollback and rethrow/report as a real failure.

### 5.3 Existing SQL (verbatim; the contract)
HOLD (single statement, no explicit transaction; 1 row = HELD, 0 rows = UNAVAILABLE):
```
UPDATE slots SET held_by = ?, held_until = now() + make_interval(secs => ?)
 WHERE id = ? AND status = 'AVAILABLE' AND (held_until IS NULL OR held_until < now());
```
CONFIRM (one transaction; 0 rows on the first statement = rollback, REJECTED_HOLD_EXPIRED; SQLState 23505 on the second = rollback, REJECTED_DUPLICATE):
```
UPDATE slots SET status = 'BOOKED', held_by = NULL, held_until = NULL
 WHERE id = ? AND status = 'AVAILABLE' AND held_by = ? AND held_until > now();
INSERT INTO bookings (slot_id, user_id) VALUES (?, ?);
```
CANCEL (one transaction, slot row locked FIRST):
```
SELECT id FROM slots WHERE id = ? FOR UPDATE;
UPDATE bookings SET status = 'CANCELLED', cancelled_at = now()
 WHERE slot_id = ? AND user_id = ? AND status = 'CONFIRMED';      -- 0 rows -> NOTHING_TO_CANCEL
UPDATE slots SET status = 'AVAILABLE' WHERE id = ?;
```
MODIFY: hold the new slot (HOLD), then ONE transaction: lock both slot rows ascending, consume new hold, cancel old booking, free old slot, insert new booking. Any failure rolls back everything.
AVAILABILITY VIEW (read only, never authoritative, may be stale the instant it returns):
```
SELECT id, start_time, end_time,
  CASE WHEN status='BOOKED' THEN 'BOOKED' WHEN held_until > now() THEN 'HELD' ELSE 'AVAILABLE' END AS display_status
FROM slots WHERE resource_id = ?;
```
INVARIANT (must return NO rows after every run; a returned row means a double booking; 0 rows is the PASS result):
```
SELECT slot_id, COUNT(*) FROM bookings WHERE status='CONFIRMED' GROUP BY slot_id HAVING COUNT(*) > 1;
```
Lock helper (concept): sort the ids ascending, then `SELECT id FROM slots WHERE id = ? FOR UPDATE` one by one; return false if any row is missing.

### 5.4 Outcome enum and SQLState map
Outcome (final, 10 values): INVALID, UNAVAILABLE, HELD, REJECTED_HOLD_EXPIRED, REJECTED_DUPLICATE, CONFIRMED, CANCELLED, NOTHING_TO_CANCEL, MODIFIED, RETRY. (REJECTED_HOLD_EXPIRED also covers "the hold was never yours".) Do not rename or remove values; new railway outcomes go in a NEW enum.
| SQLState | Meaning | Action |
|---|---|---|
| 23505 | unique violation (duplicate confirmed booking) | rollback; REJECTED_DUPLICATE |
| 40P01 | deadlock detected | rollback; RETRY |
| 55P03 | lock timeout | rollback; RETRY |
| 57014 | statement timeout | rollback; report |
| 25P03 | idle-in-transaction timeout (connection terminated) | report |
| 23503 | foreign key violation (used on purpose in Scenario 9) | expected only in that test |
| 53300 | too many connections | report as capacity failure, count separately |
| 08001 | cannot connect (seen as CONN-08001 in the 1000-thread run) | count separately as "other", never as a rejection |
| other | real failure | rollback; rethrow |

### 5.5 Why the design works (keep this reasoning in README and viva notes)
- When an UPDATE changes a row, PostgreSQL marks it; another writer waits. When the waiter wakes (READ COMMITTED) it re-reads the NEWEST row version and re-tests the WHERE clause; if now false it updates 0 rows. This works only because the check is inside the same statement as the write.
- A plain SELECT takes no row lock and never waits (MVCC), so the availability view never blocks bookings and can be stale.
- Locks are per row: requests for different berths never wait for each other (Scenario 6).
- The baseline failed even though PostgreSQL locked correctly: the lock only ordered the writes; the decision "berth is free" was made earlier, outside any lock.
- Race condition needs four things: shared resource, concurrent access, at least one write, no synchronization. Only the fourth can be removed. One-at-a-time testing never finds it, hence the start-gated harness.
- Deadlock: mutual exclusion (required, it IS the fix), hold-and-wait (accepted), no preemption (broken for the berth because the hold expires on its own), circular wait (PREVENTED by global lock order). Backstops: PostgreSQL deadlock detector (about 1 s, 40P01) and the three timeouts.
- Pessimistic style (row lock in the conditional UPDATE) suits many users on one berth (Tatkal opening). The partial unique index behaves like commit-time validation (optimistic).
- Durability is PostgreSQL's write-ahead log flushed (fsync) at commit; atomicity is rollback; Java only supplies transaction boundaries. Our code decides HOW to use them: one-statement check-and-write, transaction boundaries, lock order, error mapping, timeouts, tests.

---

## 6. DATABASE DESIGN FOR ADDS

### 6.1 Separate databases (decided, so the old evidence is never disturbed)
- `bookingdb`: the original engine database. Unchanged. Scenarios 1-9, Cancel, Modify, Durability, Scenario2Large keep running against it with Db.java exactly as today. Their baseline needs the index ABSENT only for Scenario 1; leave the index IN PLACE afterwards.
- `addsdb`: the railway application database (owner booking_app). Created by me as superuser: `CREATE DATABASE addsdb OWNER booking_app;` (give me the exact commands and the verification query).
- `addsdb_test`: a scratch copy of the same schema used for destructive tests (reset, index-off control runs, load tests). Never run a destructive test on addsdb.
- Reason: schema.sql drops and recreates tables and reset.sql frees EVERY slot; running them in the app database would wipe railway data.
- The database name/URL, user and password come from environment variables / Spring properties: ADDS_DB_URL (default jdbc:postgresql://localhost:5432/addsdb), DB_USER, DB_PASSWORD. Never hard-code credentials.

### 6.2 Schema principle
Keep the engine tables with the SAME names and the SAME columns used by BookingService, so the engine SQL runs unchanged:
- `users`(id SERIAL PK, name VARCHAR(100) NOT NULL) + extra nullable columns.
- `slots` = the berth inventory (one row per bookable berth per train run per class). Required engine columns: id, resource_id, start_time, end_time, status ('AVAILABLE'|'BOOKED' CHECK), held_by FK users, held_until TIMESTAMP.
- `bookings` = one row per passenger-berth: id, slot_id FK, user_id FK, status ('CONFIRMED'|'CANCELLED' CHECK), created_at DEFAULT now(), cancelled_at + extra nullable columns.
- `resources` = the TRAIN (resource_id on slots points to the train row, as in the existing model).
- The partial unique index one_confirmed_booking_per_slot is created in the schema file from the start (in addsdb and addsdb_test).
- All new schema is written as an idempotent, additive SQL file (`sql/adds_schema.sql`): CREATE TABLE IF NOT EXISTS, ALTER TABLE ... ADD COLUMN IF NOT EXISTS, no DROP of data in addsdb. A separate `sql/adds_reset_test.sql` exists only for addsdb_test. Normalisation: 1NF/3NF throughout (atomic columns, one row per passenger, no arrays of business data in core tables). The ONE deliberate redundancy is slots.status repeating what bookings imply, kept so the hold can be taken with one conditional UPDATE (say this in the README).

### 6.3 Tables (names are fixed so everything stays consistent)
| Table | Columns (key points) |
|---|---|
| stations | id SERIAL PK, code VARCHAR(10) UNIQUE NOT NULL, name VARCHAR(100) NOT NULL, zone VARCHAR(5) NOT NULL |
| resources (trains) | id PK, train_no VARCHAR(10) UNIQUE, name VARCHAR(100), train_type VARCHAR(20) |
| train_stops | train_id FK, stop_seq INT, station_id FK, arr_time TIME, dep_time TIME, day_offset INT, distance_km INT; PK(train_id, stop_seq); UNIQUE(train_id, station_id) |
| train_runs | id PK, train_id FK, run_date DATE (date of departure from origin), UNIQUE(train_id, run_date) |
| run_classes | run_id FK, class_code CHECK IN ('SL','3A','2A','CC','2S','3E','1A'); tatkal_berths must be 0 when class_code = '1A' (CHECK), coaches INT, general_berths INT, tatkal_berths INT; PK(run_id, class_code) |
| slots | engine columns above + run_id FK, class_code, coach_no VARCHAR(4), berth_no INT, berth_type VARCHAR(4), quota CHECK IN ('GENERAL','TATKAL'); UNIQUE(run_id, class_code, coach_no, berth_no) |
| users | id, name + email, mobile, password_hash, aadhaar_verified BOOLEAN DEFAULT false, created_at (all nullable except as noted) |
| sessions | token_hash PK, user_id FK, created_at, expires_at |
| reservations | id PK, pnr CHAR(10) UNIQUE NOT NULL, user_id FK, run_id FK, from_station_id FK, to_station_id FK, class_code, quota, status CHECK IN ('CONFIRMED','PARTIALLY_CANCELLED','CANCELLED'), base_fare NUMERIC(10,2), tatkal_charge NUMERIC(10,2), total_fare NUMERIC(10,2), created_at DEFAULT now(), cancelled_at |
| bookings (extended) | + reservation_id FK, passenger_name VARCHAR(100), age INT CHECK 1..120, gender CHECK IN ('M','F','T'), berth_preference, fare_share NUMERIC(10,2) |
| booking_drafts | id UUID PK, user_id FK, run_id FK, class_code, quota, from/to station ids, slot_ids (INT[] is allowed here only because this is a short-lived scratch row), passengers JSONB, fare JSONB, expires_at TIMESTAMP, state CHECK IN ('PENDING','PAID','FAILED','EXPIRED') |
| idempotency_keys | key VARCHAR(64), user_id FK, endpoint VARCHAR(40), response JSONB, created_at; PK(key, user_id, endpoint) |
| config_rules | key VARCHAR(60) PK, value TEXT, note TEXT (windows, fare rates, Tatkal charges, refund slabs, limits, quota share) |
| tatkal_demo_override | run_id FK PK, open_until TIMESTAMP (demo mode only) |

Berth layout per coach (used by the generator): SL 72 berths (LB, MB, UB, SL, SU), 3A 64 (LB, MB, UB, SL, SU), 2A 48 (LB, UB, SL, SU), CC 78 seats (WS, MS, AS), 2S 108 seats (WS, MS, AS), 3E 80 (LB, MB, UB, SL, SU), 1A 24 (LB, UB). The 2S, 3E and 1A layouts are demo assumptions, not official figures (added 25 Nov 2026). `slots.start_time`/`end_time` = departure from origin / arrival at final stop of that run (whole-run inventory). Tatkal berths are separate rows (quota = 'TATKAL'); GENERAL requests only ever touch quota = 'GENERAL' rows and TATKAL requests only quota = 'TATKAL' rows. The Tatkal share per class is a config value (default 30% rounded down, labelled in the README as a demo assumption, not an official figure).

Simplification to disclose: berth inventory is per whole run (a booked berth is blocked from origin to final stop); no segment-wise reuse of a berth between intermediate stations. From/To stations only affect distance and fare.

### 6.4 Seed and data honesty
- stations: real names/codes from data/stations.json AFTER cleaning (6.5). REAL part.
- trains, timetables, run dates, berth inventory, users (50 existing + generated up to 10,000), fares: GENERATED. SYNTHETIC. Train names must be obviously generated (for example "ADDS Express 1"), not real train numbers or real timetables, so nobody can mistake them for real services. Every page that shows train data carries the demo footer. The README has a table "which data is real and which is synthetic".

### 6.5 Station cleaning rules (script, repeatable, logged)
Use only code, name and zone. Drop rows whose code does not match ^[A-Z]{1,5}$ (removes 'XX-' placeholders); drop rows whose name equals the code; drop siding/cabin/yard/goods/workshop/colliery/depot/block hut/flag station names by keyword list; check duplicate codes (keep the first, log the dropped ones). Valid zones are the 16 zone codes plus KR; '?' and null mean missing, and a missing zone does not drop a row. Then take about 100-200 major stations from a hand-checked whitelist data/major_stations.csv (code, zone): every whitelisted code must survive the earlier steps or the script fails; where the source has a valid zone it must equal the whitelist zone or the script fails; where the source zone is missing the whitelist zone is used and listed in the log. Codes and names are kept exactly as in the source (2016 versions). Output: sql/stations_seed.sql (INSERT ... ON CONFLICT (code) DO NOTHING) plus a log file in evidence/ stating counts at every step.

---

## 7. BUSINESS RULES (GENERAL and TATKAL)

These come from a web check on 25 Nov 2026. Rail rules change often. Store every number below in config_rules (not hard-coded), show "rules as of 25 Nov 2026" in the README, and tell me which ones you could not confirm. Do not present them as official.

### 7.1 General quota
- Advance reservation period (ARP): 60 days before the journey date, excluding the journey date. Booking opens 08:00 on the opening day (config).
- Journey date = date the train departs from its ORIGIN station (train_runs.run_date).
- Booking closes at chart preparation = 4 hours before the scheduled departure from origin (config; one place decides this).
- Max 6 passengers per reservation. Per user per calendar month: 6 tickets, or 12 if aadhaar_verified (config).
- Berth preference (LB, MB, UB, SL, SU, none) is only a preference; the server allocates (see 8.5).

### 7.2 Tatkal quota
- Window: opens ONE day before the journey date (journey date excluded), at 10:00 for AC classes (2A, 3A, CC, 3E) and at 11:00 for non-AC classes (SL, 2S), IST, evaluated with the DB clock. Closes at chart preparation like general.
- Max 4 passengers per reservation. No concession of any kind. Aadhaar-authenticated user required; an OTP step is simulated (fixed demo OTP "123456", shown on the screen with a DEMO label).
- Tatkal charge per passenger, added to the base fare: 10% of base fare for 2S, 30% for every other class, each bounded by a minimum and maximum: 2S 10-15; SL 100-200; CC 125-225; 3A/3E 300-400; 2A 400-500 (config table). 1A and EC have no Tatkal in ADDS.
- Cancelling a CONFIRMED Tatkal ticket gives NO refund (the cancel is allowed, refund = 0). Waitlisted Tatkal is not built.
- Demo mode: because nobody can wait for 10:00, an admin demo endpoint may set tatkal_demo_override for a chosen run for N minutes. While active, the UI shows a visible "DEMO OVERRIDE ACTIVE" banner. The real rule (DB clock) is the default and is tested separately with a pure function that takes the timestamp as a parameter.

### 7.3 Fare (demo formula, disclose it)
base_fare per passenger = round(distance_km between from and to stops x per-km rate for the class) + fixed reservation charge for the class; per-km rates and charges come from config_rules; AC classes add 5% GST (config). Tatkal adds the Tatkal charge. These are NOT official fares. Show a fare breakdown on the review and ticket pages.

### 7.4 Cancellation and refund (config table; verify before submission)
- General CONFIRMED: refund = fare minus a deduction. Default slabs (changed from 1 April 2026 per news reports; mark "verify"): more than 72 h before departure: flat clerkage per passenger by class (SL 120, 3A/3E/CC 180, 2A 200, 1A 240, 2S 60); 72-24 h: 25% of fare (not less than the flat amount); 24-8 h: 50% of fare (not less than the flat amount); less than 8 h: no refund.
- Tatkal CONFIRMED: no refund.
- Cancelled bookings are kept with status CANCELLED and cancelled_at; the berth becomes AVAILABLE again and can be rebooked (this is what the partial index allows).
- The refund preview endpoint and the cancel endpoint use the SAME pure function.

---

## 8. FUNCTIONAL DESIGN

### 8.1 Users and sessions (demo grade)
Register (name, email, mobile, password) and login. Hash passwords with BCrypt (spring-security-crypto only; do not add the full Spring Security framework). A random opaque session token (32 bytes), only its hash stored in `sessions`, sent as an HttpOnly cookie. The 10,000 generated load-test users have no password and cannot log in; five demo accounts demo1..demo5 exist with a demo password written in the README and flagged as demo. aadhaar_verified is a flag the user can toggle through a simulated "verify Aadhaar" screen (no real Aadhaar).

### 8.2 Search
Inputs: from station, to station, journey date, class (optional), quota (GENERAL default | TATKAL). Output per train: number, name, departure/arrival, duration, distance, running days (all days in demo), and per class: fare and availability text `AVAILABLE-<n>` or `NOT AVAILABLE` (NOT AVAILABLE when no berth is free; sold-out never shows WL). Availability count = number of berths with status AVAILABLE and not currently held, for that quota, read with the AVAILABILITY VIEW pattern (non-authoritative; the screen says "availability may change"). For a Tatkal search outside the window: show the train but disable Book with the exact opening time.

### 8.3 Reservation flow (state machine)
SEARCH -> SELECT CLASS -> PASSENGER DETAILS -> HOLD (berths held, countdown starts) -> REVIEW -> SIMULATED PAYMENT (Tatkal: Aadhaar OTP step first) -> CONFIRM (one transaction) -> TICKET/PNR. Failure/timeout branches: payment failed or timed out -> no booking, hold expires on its own (lazy expiry); hold expired before pay -> REJECTED_HOLD_EXPIRED -> screen says "your hold expired, search again"; duplicate/duplicate-click -> idempotency returns the original result.

### 8.4 Passenger rules (server-side validation, Section 10)
name 1-60 letters/space; age 1-120; gender M/F/T; berth preference from a fixed list; count within the quota's limit; at least one adult (age >= 12) per reservation; duplicate passenger (same name+age+gender in the same reservation) rejected.

### 8.5 Berth allocation (NEW concurrency path; must be proven before use)
The server allocates; the user does not pick a berth. For a reservation of n passengers, in class C and quota Q on run R, in ONE short transaction and ONE statement:
```
UPDATE slots SET held_by = ?, held_until = now() + make_interval(secs => ?)
 WHERE id IN (
   SELECT id FROM slots
    WHERE resource_id = ? AND run_id = ? AND class_code = ? AND quota = ?
      AND status = 'AVAILABLE' AND (held_until IS NULL OR held_until < now())
    ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED)
RETURNING id;
```
If fewer than n rows are returned: ROLLBACK (nothing is held) and answer UNAVAILABLE. If n rows: COMMIT and return the held ids. Berth preference: first try rows of the preferred berth_type with the same statement shape; fall back to any berth if not enough; document this in the README. Properties to state honestly: SKIP LOCKED never waits, so it cannot deadlock; under heavy contention a request may be refused although enough berths would be free a moment later (spurious UNAVAILABLE; the client may retry); the winner order is arrival order, not fairness. The hold length is a parameter: default 300 seconds (5 minutes); demos and tests use 5 seconds (or 15-30 s). Payment-page countdown reads held_until from the server; the server (not the browser) decides expiry again at confirm.
This path is NEW. It is not trusted until scenarios R11 and R12 (Section 13) pass. Show me the code and the plan before running it.

### 8.6 Confirm (one transaction, all passengers)
1. setAutoCommit(false). 2. Use lockSlotsInOrder over all held ids (ascending). 3. For each id run CONSUME (0 rows -> rollback, REJECTED_HOLD_EXPIRED). 4. INSERT the reservations row (PNR generated from a database sequence mapped to a 10-digit number, unique) and one bookings row per passenger (slot_id, user_id, reservation_id, passenger fields, fare_share). 5. SQLState 23505 -> rollback, REJECTED_DUPLICATE; 40P01/55P03 -> rollback, RETRY (bounded retry: 3 attempts, short backoff, then 503 "busy"). 6. commit. 7. setAutoCommit(true) in finally. The Tatkal window check (DB clock) happens in the same transaction before CONSUME using `SELECT now()`. The booking is real only after COMMIT; never tell the user CONFIRMED before commit returns.

### 8.7 Simulated payment
Payment screen offers three buttons that only choose a simulated outcome: Pay success, Pay failure, Pay timeout (waits until the hold expires). Success -> calls confirm. Failure/timeout -> draft state FAILED/EXPIRED, no booking row, berths free themselves when the hold expires (lazy expiry; there is no early-release feature unless I approve one). This demonstrates "payment fails -> consistency preserved by rollback/expiry, not by leaving the berth held forever".

### 8.8 Cancel
One transaction per cancellation request: lockSlotsInOrder over the berths of the passengers being cancelled, then per passenger the CANCEL statements, then reservation status update (PARTIALLY_CANCELLED or CANCELLED), refund computed by the shared pure function and stored. 0 rows -> NOTHING_TO_CANCEL. Partial passenger cancellation is allowed for General and for Tatkal (refund rule per 7.4).

### 8.9 Modify
The engine's modify() stays tested and untouched. ADDS does not expose it in the UI unless a screenshot shows a "change journey/boarding" feature; if it does, ask me first.

### 8.10 Idempotency
Hold and pay requests carry a client-generated Idempotency-Key header. The server stores the key with the response in the SAME transaction as the booking (INSERT ... ON CONFLICT DO NOTHING pattern with the stored result returned on a repeat). A double click or network retry returns the original result and creates no second booking. The partial unique index remains the backstop.

---

## 9. SPRING BOOT ARCHITECTURE

### 9.1 Stack (fixed)
Java 21, Maven, Spring Boot 3.5.16 (as chosen; if Maven cannot resolve it, STOP and tell me, do not change the version silently), spring-boot-starter-web, spring-boot-starter-jdbc (HikariCP), PostgreSQL driver 42.7.4, spring-security-crypto (BCrypt only), spring-boot-starter-test + JUnit 5 for tests. No JPA/Hibernate, no Spring Data, no jOOQ, no Lombok, no WebFlux, no Redis, no message broker. Virtual threads on: `spring.threads.virtual.enabled=true`. The pom keeps exec-maven-plugin 3.5.0 so every scenario still runs with exec:java; the spring-boot-maven-plugin mainClass is booking.web.BookingApplication.

### 9.2 Configuration (externalised; nothing hard-coded)
src/main/resources/application.properties with environment overrides:
- spring.datasource.url=${ADDS_DB_URL:jdbc:postgresql://localhost:5432/addsdb}, username ${DB_USER}, password ${DB_PASSWORD} (no default for the password).
- Hikari: minimum-idle 5, maximum-pool-size 30 (start value; allowed range 20-50, always far below max_connections=100; tuned only by measurement), connection-timeout 3000 ms (bounded acquisition so excess requests fail fast with 503 instead of waiting forever), max-lifetime and idle-timeout defaults, auto-commit true, connection-init-sql sets: time zone Asia/Kolkata, lock_timeout 2s, statement_timeout 5s, idle_in_transaction_session_timeout 10s. These values live in properties, not in Java.
- adds.hold.seconds=300 (demo/test override 5), adds.retry.max=3, adds.* business rule defaults only as fall-backs; the real numbers are read from config_rules.
- A `demo` profile enables admin/demo endpoints, protected by an ADMIN_TOKEN environment variable; without the profile they do not exist.
- Port 8080 (env override). Health endpoint GET /api/health (checks one `SELECT 1` through the pool).

### 9.3 Packages and layering (controller -> service -> repository)
- `booking` (existing engine and scenarios): untouched.
- `booking.web`: BookingApplication, controllers, request/response DTOs, global exception handler, request-id filter.
- `booking.railway`: services (SearchService, ReservationService, CancellationService, FareService, TatkalWindowService, RefundService, AuthService, DemoAdminService), repositories (all SQL lives here), RailwayOutcome enum.
- `booking.load`: the load generator.
- Keep the transaction and lock-order logic TOGETHER in one place (ReservationService/repository calling the engine pattern) so Scenarios 8 and 9 behave the same. Pure business rules (fare, Tatkal window, refund, validation) are pure functions with no database access so they can be unit-tested.
- Controllers contain no SQL and no business rules. Repositories contain only SQL with `?` placeholders.

### 9.4 REST API (JSON)
| Method and path | Purpose | Success | Errors |
|---|---|---|---|
| POST /api/auth/register, /login, /logout; GET /api/me | demo auth | 200/201 | 400, 401, 409 (email taken) |
| POST /api/me/aadhaar-demo-verify | set simulated flag | 200 | 401 |
| GET /api/stations?q= | station autocomplete | 200 | 400 |
| GET /api/trains/search?from=&to=&date=&quota=&class= | train list with availability and fare | 200 | 400 |
| GET /api/runs/{runId}/availability?class=&quota= | counts for one class | 200 | 404 |
| POST /api/reservations/hold | validate, check window/limits, allocate and hold berths, create draft | 201 {draftId, heldUntil, fare} | 400, 401, 403 (window closed / not Aadhaar-verified), 409 (UNAVAILABLE), 503 |
| POST /api/reservations/{draftId}/pay | simulated payment then confirm | 200 {pnr} | 409 (hold expired / duplicate), 402-style payment failure as 200 with state FAILED, 503 |
| GET /api/reservations ; GET /api/pnr/{pnr} | history; PNR status | 200 | 404 |
| GET /api/reservations/{pnr}/refund-preview ; POST /api/reservations/{pnr}/cancel | refund preview; cancel (all or selected passengers) | 200 | 404, 409 |
| Demo profile only: GET /api/admin/invariant, GET /api/admin/counts, POST /api/admin/demo/open-tatkal, POST /api/admin/demo/simulate, POST /api/admin/demo/reset (test DB only, refuses on addsdb) | demo panel | 200 | 401 |
HTTP mapping: validation 400; unauthenticated 401; not allowed (window closed, no Aadhaar flag, limit exceeded) 403; not found 404; berth taken, hold expired, duplicate 409; pool timeout, lock timeout after retries 503; unexpected 500 with no stack trace in the body. Every response carries the request id.

---

## 10. INPUT VALIDATION (five layers; explain them in the README)
1. Outer boundary (controllers): type, range, required, format, length, enum values checked server-side before any database work; failures return 400 and never take a connection or a lock. Browser checks are only convenience.
2. Safe hand-off: values reach SQL only as bound parameters (PreparedStatement `?`); never string-concatenate SQL (this blocks SQL injection).
3. State validation done atomically: availability, hold ownership and expiry are checked inside the conditional UPDATE, not in Java.
4. Constraints as the last line: CHECK constraints for status/quota/class/gender, NOT NULL, foreign keys, and the partial unique index.
5. Controlled failure: SQLState mapped to meaningful outcomes (23505 slot already taken, 40P01 deadlock retry, timeouts busy); transactions roll back so no half-written data remains.

---

## 11. OBSERVABILITY AND LOGGING
- Every request gets a request id (generated in a filter, put in MDC, returned in a response header). Every log line for a booking carries: timestamp, request id, user id, run id, class, quota, outcome, SQLState if any, elapsed ms.
- Execution checkpoints logged per booking: REQUEST_RECEIVED, VALIDATED, HOLD_ACQUIRED / HOLD_REFUSED, TX_STARTED, CONSUME_OK / CONSUME_REFUSED, INSERT_OK / DUPLICATE, COMMIT / ROLLBACK, RESPONSE_SENT.
- Logs are structured (key=value or JSON, one line each) so entries from different layers can be joined into one trace.
- Scenario/load logs are written to evidence/ with timestamped file names, one per run, and committed. Never put secrets in them.
- Latency: record per-request elapsed time in the load test and report p50/p95/p99 and total duration ONLY from real measurements.

---

## 12. FRONTEND (decided)

Plain HTML + CSS + vanilla JavaScript in src/main/resources/static. No Node, no npm, no build step, no frameworks (including Vue/React). Responsive, accessible (labels, keyboard, contrast), no external CDN, no tracking. A small shared JS module for fetch with request ids and Idempotency-Key.
Pages (final list comes from the screenshots; this is the default if a screenshot is missing): Home/Search (with a General/Tatkal quota choice and a visible Tatkal opening-time note), Train list with class cards and availability, Passenger details, Review journey with fare breakdown and hold countdown, Payment (simulated), Ticket/PNR, My Bookings, PNR Status, Cancel with refund preview, Login/Register, Demo Panel (demo profile only: simulate N users on a run/class, show held/rejected/confirmed counts, run the invariant check, open the Tatkal window in demo mode, reset the TEST database).
The browser never decides availability or expiry. The hold countdown reads held_until from the server; the server re-checks at confirm.
Match the screenshots for layout, field names, labels and order. Do not copy logos, images or distinctive wording of any real organisation (Section 1). Where a screenshot shows an out-of-scope feature, leave it out and list it for me.

---

## 13. TESTING AND EVIDENCE (methodology; do not skip any)

### 13.1 Method
Reproduce the defect first, then add one layer at a time and test each layer on its own. Concurrency is created with a START-GATED harness: Java virtual threads released together by a CountDownLatch, one database connection per thread (or one pooled connection per request in the web load test). After every run the INVARIANT query checks the database itself; the Java output only shows what each thread saw. Failure and restart cases are forced deliberately. Every run is logged to evidence/ and committed.

### 13.2 Regression (must keep passing, unchanged, against bookingdb)
Scenario 1 baseline defect (meant to fail: 20 of 20 told CONFIRMED, slot has 20 confirmed rows; index must be absent; restore it afterwards), RaceTrace (3 users READ/WRITE/COMMIT), Scenario 2 backstop (1 success, 19 rejected with 23505, invariant 0 rows), Scenario 3 hold contention (HELD=1, UNAVAILABLE=19), Scenario 4 confirm contention (CONFIRMED=1, REJECTED_HOLD_EXPIRED=19), Scenario 5 expiry (5 s hold, 7 s wait; B refused, then B held after expiry, A's late confirm REJECTED_HOLD_EXPIRED, B CONFIRMED), Scenario 6 different slots (6 of 6 confirmed), Scenario 7 durability (confirm, stop PostgreSQL, restart, 1 confirmed row; claim "abrupt crash" only if `pg_ctl stop -m immediate` was run and its output screenshotted; otherwise say "survived a service stop and restart"), Scenario 8 lock order (control: one deadlock 40P01; ordered locking 10 of 10), Scenario 9 atomicity (forced FK failure 23503 inside confirm; after rollback slot AVAILABLE, held_by kept, 0 booking rows), CancelTest, ModifyTest. Scenario numbers 1-9 are fixed; never renumber. After the Spring Boot pom change, rerun at least Scenario 2 and show it.

### 13.3 New railway scenarios (numbers R10 onward; they run on addsdb_test)
| # | Scenario | Expected |
|---|---|---|
| R10 | Tatkal window rules as a pure function with injected timestamps: AC just before/at 10:00, non-AC just before/at 11:00, day before vs two days before, journey day itself closed, chart-time closing | exact boundary results; unit tests |
| R11 | Multi-passenger hold contention: many users each ask n berths of one class with fewer berths free than total demand | no berth held twice; held berths <= free berths; every success holds exactly n; shortfall requests UNAVAILABLE (spurious UNAVAILABLE counted and explained) |
| R12 | Tatkal surge: N users (start at 1000 via pool, then 10,000) request 1 berth each on one run/class with M Tatkal berths | confirmed rows = M (when every holder pays), invariant 0 rows, all others 409; "other" errors counted separately |
| R13 | Cancel versus new hold on the same berth at the same moment | invariant 0 rows; cancelled row kept; rebook allowed |
| R14 | Payment failure and payment timeout | no booking row; berths AVAILABLE after expiry; draft state FAILED/EXPIRED |
| R15 | Double-click/retry with the same Idempotency-Key (many parallel identical requests) | exactly one reservation, identical responses |
| R16 | Multi-passenger confirm with opposite id orders from two users | ordered locking: no deadlock; control without the helper (in addsdb_test only) shows 40P01 |
| R17 | Pool exhaustion: more simultaneous requests than pool size | bounded 503s, no data corruption, invariant 0 rows; the 1000-thread failure mode (CONN-08001, 750 of 1000) must not recur through the pool |
| R18 | 10,000-user load test (see 13.4) | measured counts only |
| R19 | Rules on cancel/refund: slab boundaries and Tatkal no-refund | unit tests |
Also: an API smoke test with curl.exe (or Invoke-RestMethod) for every endpoint, success and error cases, saved to evidence/.

### 13.4 The 10,000-user load test
- 10,000 simulated users, started together by the gate, calling the REAL HTTP API (or the service layer, stated clearly) through the bounded pool; never 10,000 database connections. Pool size and connection timeout are tuned from measured wait times and recorded.
- Report counts separately: confirmed, held-but-not-paid, 409 (rejected), 503 (pool/lock timeouts), other errors (with the first error line), total duration, and latency p50/p95/p99 from real measurements. RESULT is PASS only if the invariant holds AND other = 0; otherwise print INCONCLUSIVE (the 5 Nov 1000-thread run printed "FAIL or inconclusive": 1 success, 249 rejected, 750 other CONN-08001, invariant held, only 250 of 1000 actually competed; keep that log as honest evidence).
- Run it first with 100, then 1000, then 10,000, on addsdb_test only. Watch memory; stop if the laptop struggles; never raise max_connections.
- Repeat each concurrency scenario 100 times in a repeat-runner that logs the pass count (the KPI "100 runs, zero double bookings" is NOT yet measured; do not claim it until measured).
- Do not state throughput or requests-per-second unless measured.

### 13.5 Test pyramid
Base: fast unit tests (pure rules: fare, Tatkal window, refund, validation, PNR format). Middle: integration tests against a real PostgreSQL (the engine and scenario classes; races only show against a real database; H2 or mocks are NOT acceptable for concurrency tests). Top: a few end-to-end tests and the load test. State honestly which layers exist.

### 13.6 Evidence discipline
One timestamped log per run in evidence/, committed and pushed. Each log ends with an invariant line and a RESULT line. A test-case table (README) lists each scenario, expected, actual, evidence file, machine, and for Scenario 1 and the load test the words "meant to fail" / "measured on this laptop". No fabricated numbers.

---

## 14. PHASES (stop after each one; show the real output; commit and push; wait for my OK)

- Phase 0: verify state. `git status`, branch springboot, `mvn -q compile`, rerun Scenario 2 (expect successes=1 rejected_23505=19 other=0 and "RESULT: invariant holds, no double booking"), commit the pom change.
- Phase 1: Spring Boot skeleton: BookingApplication, application.properties, Hikari pool, /api/health, request-id filter, global exception handler. Prove the nine scenarios still compile and Scenario 2 still passes. No railway code yet.
- Phase 2: create addsdb and addsdb_test; adds_schema.sql (additive, idempotent, index included); verify with \d outputs; commit.
- Phase 3: data generator + station cleaning: clean stations, generate trains, stops, runs, berths (slots), 10,000 users, config_rules; counts printed and logged; disclose real vs synthetic.
- Phase 4: pure rule classes with unit tests (Tatkal window, fare, refund, validation). Scenario R10 and R19.
- Phase 5: auth and search API with curl tests.
- Phase 6: allocation + hold + confirm + payment simulation + idempotency. Show the code first. Scenarios R11, R14, R15, R16 pass.
- Phase 7: cancel and refund. R13.
- Phase 8: frontend (screens from screenshots), screen by screen, each checked in a browser with a screenshot from me.
- Phase 9: Tatkal surge and load tests R12, R17, R18 at 100, 1000, 10000; repeat-runner; measure and report honestly.
- Phase 10 (optional, only if I say yes): waitlist FIFO with promotion in one transaction.
- Phase 11: README (run steps, architecture, test-case table, disclosures, real-vs-synthetic data table, rules as of date), Whiteboard/diagram update, final secrets scan, final push, tag.

---

## 15. DISCLOSURES TO KEEP TRUE IN EVERY DOCUMENT
- The 50 ms sleep in the baseline only widens the race window; real systems get the overlap from load. Demo holds are 5 s; the default is 300 s.
- Scenario 1 is MEANT to fail (it documents the defect). Scenario 1 evidence came from the old laptop (Java 21.0.12.1); Scenarios 2-9 from the current one (Java 21.0.13). Scenario 8's control deadlock is a single run: it shows the problem can happen, not how often; the 10-of-10 result is consistent with the ordering argument, and the guarantee comes from the ordering rule. Scenario 6's 61-89 ms is one run on one machine, not a benchmark.
- Most scenarios were run once or a few times: this is evidence of correct behaviour, not statistical proof. The 100-run KPI and latency percentiles are measured only when Phase 9 produces them.
- Fairness: exactly one winner, not fair or predictable. No waitlist, so no bounded waiting.
- Payment is simulated by the hold window and a simulated outcome. Real payments would need TLS, CSRF protection, tokenisation, signed callbacks, idempotency keys and refund handling: not built.
- No CI/CD (a shared CI runner would make the timing-sensitive race tests unreliable).
- Gate 0 evidence (observations/interviews) was never collected; results exist only because the code was run.
- Fares, timetables, trains, users and the Tatkal share are synthetic or demo assumptions. Rules are from a web check on 25 Nov 2026 and may change.
- Station codes and names are 2016 versions from DataMeet (CC0), not a current official list; zones for some stations come from a hand-checked whitelist because the source has none.
- Berth inventory is per whole run (no segment-wise reuse).
- SKIP LOCKED allocation can refuse a request that would succeed a moment later.
- Schema management is plain additive SQL; Flyway-style versioned migrations would be the next step for a cloud deployment (additive, backward-compatible changes first, old columns removed last). Config would be overridden by environment variables in the cloud; the pool must stay smaller than the cloud database's connection limit; CIDR-restricted database access would be used there.

---

## 16. KNOWN MISTAKES TO AVOID (from earlier sessions)
1. winget has no Apache Maven package (Maven is already installed in C:\tools).
2. winget PostgreSQL download fails with 403; PostgreSQL is already installed.
3. A new PATH is not visible in an already-open window; open a new terminal.
4. Set-Content -Encoding UTF8 writes a BOM and breaks javac.
5. I type placeholders literally; always verify (password length check, psql login).
6. Never type setx or shell commands inside psql (prompt turns into postgres-#; Ctrl+C then \q).
7. psql "password authentication failed" after blind typing: use `$env:PGPASSWORD = $env:DB_PASSWORD` (psql reads PGPASSWORD; Java reads DB_PASSWORD).
8. booking_app cannot SHOW data_directory (expected).
9. Start-Service/pg_ctl need an Administrator PowerShell.
10. In the admin window the current directory is C:\WINDOWS\system32; run mvn and evidence logging in the normal tab inside the project.
11. README pastes get cut at triple backticks; write long files in parts.
12. Commit and push after every step; uncommitted work is lost with the machine.
13. A screenshot often does not show the commit/push or pg_ctl output; say so and verify with `git log --oneline` or ask again.
14. The handoff's example slot pair (5, 7) for Scenario 8 does not exist in the 6-slot seed; use existing ids.
15. "0 rows" from the invariant query is the PASS result, not a bug.
16. A plain UNIQUE(slot_id) is wrong; the index is partial.
17. A transaction alone does not close the check-then-write gap at READ COMMITTED; atomicity is not exclusion.
18. The unproven `? * INTERVAL '1 second'` hold syntax must not return.
19. 1000 uncapped threads on an 8 GB laptop fail to connect (CONN-08001); always cap through the pool.
20. Spring's @Transactional would silently replace the manual commit/rollback; keep manual control.
21. Do not rename Scenario 6 or renumber any scenario.
22. Do not claim Spring Boot is added until Phase 0/1 outputs are seen.

---

## 17. SELF-CHECK BEFORE EVERY REPLY (do this silently, then answer)
1. Did I follow my mode (one step or one phase) and stop for my approval?
2. Is every number, result and claim backed by an output I was actually shown? If not, I say "not shown".
3. Does any new code path read-then-write outside one statement/transaction? If yes, fix it.
4. Does any multi-row path bypass lockSlotsInOrder? If yes, fix it.
5. Did I use the DB clock and the Asia/Kolkata session zone, never the Java clock?
6. Is there a secret, placeholder or BOM anywhere in what I am giving the user?
7. Did I keep ADDS branding, avoid real-organisation branding, and keep the demo footer?
8. Did I stay in scope (General and Tatkal only, no waitlist unless approved)?
9. Is the evidence log named, timestamped, committed and pushed?
10. Is the next step exactly one step?

DEFINITION OF DONE: all regression scenarios pass; all R-scenarios in Section 13.3 pass with evidence; the invariant returns 0 rows after every run; the web app works end to end for General and Tatkal on the screens in my screenshots; the load test is reported with measured numbers and honest labels; README, test-case table and disclosures are complete; no secrets in the repository.
