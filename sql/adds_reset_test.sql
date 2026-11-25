-- ADDS test-database reset. ONLY for addsdb_test: it refuses to run on any other database.
-- Clears booking state between test runs (like sql/reset.sql does for bookingdb), keeps the
-- generated reference data (stations, trains, stops, runs, classes, berths, users, config).
-- Run as booking_app:
--   psql -v ON_ERROR_STOP=1 -U booking_app -h localhost -d addsdb_test -f sql\adds_reset_test.sql
\set ON_ERROR_STOP on
BEGIN;

-- GUARD: must be the first statement inside the transaction, before any TRUNCATE/UPDATE.
DO $$
BEGIN
  IF current_database() <> 'addsdb_test' THEN
    RAISE EXCEPTION 'adds_reset_test.sql refused: current database is "%", only addsdb_test may be reset',
                    current_database();
  END IF;
END $$;

-- Booking state only. No CASCADE: every referencing table is listed explicitly.
TRUNCATE bookings, reservations, booking_drafts, idempotency_keys, tatkal_demo_override, sessions
  RESTART IDENTITY;

-- Free every berth (no holds, nothing booked).
UPDATE slots SET status = 'AVAILABLE', held_by = NULL, held_until = NULL;

-- PNRs start again from 1000000001 on the test database.
ALTER SEQUENCE pnr_seq RESTART;

-- Put the correctness guarantee back in case an index-off control run dropped it.
CREATE UNIQUE INDEX IF NOT EXISTS one_confirmed_booking_per_slot
  ON bookings (slot_id) WHERE status = 'CONFIRMED';

COMMIT;
