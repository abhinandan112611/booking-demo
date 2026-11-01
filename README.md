# Concurrent Booking and Reservation Management System

Team 2, KLE Technological University, Foundation Integration Course mini project.

A booking system reproduces a double-booking defect under concurrent load, then fixes it with a
temporary hold, a transaction and a partial unique index. Java 21 + JDBC + PostgreSQL 17. No frontend.

## The fix in three layers

1. **Hold** (BookingService.requestHold): one conditional UPDATE, so the availability check and the write are one statement.
2. **Transaction** (BookingService.confirm, cancel, modify): all-or-nothing, slot rows always locked in ascending id.
3. **Partial unique index** (sql/add_index.sql): at most one CONFIRMED booking per slot, as a last line of defense.

All locking is done by PostgreSQL. The Java code contains no lock or synchronized code for booking correctness.

## Setup

1. Install JDK 21, Maven, Git and PostgreSQL 17.
2. Create role booking_app and database bookingdb (owner booking_app).
3. Set environment variables DB_USER and DB_PASSWORD. Credentials are never in source code.
4. Load the schema and data:

        psql -U booking_app -h localhost -d bookingdb -f sql\schema.sql
        psql -U booking_app -h localhost -d bookingdb -f sql\seed.sql

5. The baseline (Scenario 1) must run WITHOUT the index. Add it afterwards:

        psql -U booking_app -h localhost -d bookingdb -f sql\add_index.sql

## Running a scenario

        mvn -q compile exec:java "-Dexec.mainClass=booking.<ClassName>"

Each run resets the data, writes a timestamped log to evidence/, and checks the invariant
(no slot has more than one CONFIRMED booking).
## Test-case table

| ID | Scenario | Class | Expected | Actual (from logs) | Result |
|----|----------|-------|----------|--------------------|--------|
| 1 | Baseline, defect (index absent) | ConcurrencyTest, RaceTrace | More than one CONFIRMED booking on one slot | 20 of 20 and 3 of 3 confirmed on slot 1 | Defect reproduced (test is MEANT to fail) |
| 2 | Backstop alone (index present, hold bypassed) | Scenario2Backstop | 1 success, rest SQLState 23505 | 1 success, 19 rejected | PASS |
| 3 | Hold contention | Scenario3HoldContention | 1 HELD, rest UNAVAILABLE | HELD=1 UNAVAILABLE=19 | PASS |
| 4 | Confirm contention | Scenario4ConfirmContention | Only the holder confirms | CONFIRMED=1 REJECTED_HOLD_EXPIRED=19 | PASS |
| 5 | Expiry (5 s hold) | Scenario5Expiry | B holds after expiry, A's confirm rejected | As expected, no background job | PASS |
| 6 | Different slots | Scenario6DifferentSlots | All succeed | CONFIRMED=6 of 6 | PASS |
| 7 | Durability | DurabilityBefore / DurabilityAfter | Booking present after stop and restart | 1 confirmed row found | PASS (see note) |
| 8 | Lock order | Scenario8LockOrder | No deadlock with ordered locking | Control: one 40P01. Ordered: 10 of 10 OK | PASS |
| 9 | Atomicity | Scenario9Atomicity | No half-written state after forced failure | Slot AVAILABLE, 0 booking rows after rollback | PASS |

Also tested: cancel (CancelTest) and modify (ModifyTest), both PASS.

## Disclosures and limitations

- **Test aids:** the 50 ms sleep in the baseline widens the race window. Demo holds are 5 seconds; the default is 300 seconds.
- **Scenario 7:** shows the booking survived a PostgreSQL stop and restart. It is an abrupt-crash result only if the stop used pg_ctl stop -m immediate; confirm which before claiming it.
- **Few runs:** scenarios 3 to 9 were run once or a few times. The 100-run KPI (NFR-01) has NOT been measured, and no latency percentile has been measured. Do not report either.
- **Environment:** Scenario 1 evidence is from the first laptop (Windows 11, Java 21.0.12.1). Scenarios 2 to 9 are from the second (Windows 11, Java 21.0.13). PostgreSQL 17.11 on both.
- **Fairness:** exactly one winner every time, but the winner is not predictable or fair. There is no waitlist.
- **Payment** is simulated by a hold window. Roles, waitlist and reports are out of scope.
- sql/schema.sql drops and recreates all tables.