-- ADDS railway schema. Additive and idempotent: safe to run again on the same database.
-- No DROP, no DELETE, no TRUNCATE. Engine tables keep the exact names and columns that
-- BookingService uses, so the engine SQL (HOLD, CONSUME, CANCEL, lockSlotsInOrder) runs unchanged.
-- Run as booking_app:  psql -v ON_ERROR_STOP=1 -U booking_app -h localhost -d addsdb -f sql\adds_schema.sql
BEGIN;

-- Database default time zone, so psql and seed scripts see the same clock zone as the app pool.
DO $$ BEGIN
  EXECUTE format('ALTER DATABASE %I SET timezone = %L', current_database(), 'Asia/Kolkata');
END $$;

-- ===== Engine tables (same columns as sql/schema.sql) =====
CREATE TABLE IF NOT EXISTS users (
  id   SERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL
);
CREATE TABLE IF NOT EXISTS resources (           -- resources = TRAINS
  id   SERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL
);

-- ===== Reference data =====
CREATE TABLE IF NOT EXISTS stations (
  id   SERIAL PRIMARY KEY,
  code VARCHAR(10)  NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  zone VARCHAR(5)   NOT NULL
);

-- Train columns on resources
ALTER TABLE resources ADD COLUMN IF NOT EXISTS train_no   VARCHAR(10) UNIQUE;
ALTER TABLE resources ADD COLUMN IF NOT EXISTS train_type VARCHAR(20);

CREATE TABLE IF NOT EXISTS train_stops (
  train_id    INT  NOT NULL REFERENCES resources(id),
  stop_seq    INT  NOT NULL CHECK (stop_seq >= 1),
  station_id  INT  NOT NULL REFERENCES stations(id),
  arr_time    TIME,                                -- NULL at the origin
  dep_time    TIME,                                -- NULL at the final stop
  day_offset  INT  NOT NULL DEFAULT 0 CHECK (day_offset >= 0),
  distance_km INT  NOT NULL CHECK (distance_km >= 0),
  PRIMARY KEY (train_id, stop_seq),
  UNIQUE (train_id, station_id)
);

CREATE TABLE IF NOT EXISTS train_runs (
  id       SERIAL PRIMARY KEY,
  train_id INT  NOT NULL REFERENCES resources(id),
  run_date DATE NOT NULL,                          -- date of departure from the ORIGIN
  UNIQUE (train_id, run_date),
  UNIQUE (id, train_id)                            -- lets slots prove resource_id matches the run
);

CREATE TABLE IF NOT EXISTS run_classes (
  run_id         INT        NOT NULL REFERENCES train_runs(id),
  class_code     VARCHAR(3) NOT NULL CHECK (class_code IN ('SL','3A','2A','CC','2S','3E','1A')),
  coaches        INT        NOT NULL CHECK (coaches > 0),
  general_berths INT        NOT NULL CHECK (general_berths >= 0),
  tatkal_berths  INT        NOT NULL CHECK (tatkal_berths >= 0),
  PRIMARY KEY (run_id, class_code),
  CONSTRAINT run_classes_no_tatkal_1a CHECK (class_code <> '1A' OR tatkal_berths = 0)
);

-- ===== slots = berth inventory (engine columns + railway columns) =====
CREATE TABLE IF NOT EXISTS slots (
  id          SERIAL PRIMARY KEY,
  resource_id INT NOT NULL REFERENCES resources(id),
  start_time  TIMESTAMP NOT NULL,
  end_time    TIMESTAMP NOT NULL,
  status      VARCHAR(10) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE','BOOKED')),
  held_by     INT REFERENCES users(id),
  held_until  TIMESTAMP
);
ALTER TABLE slots ADD COLUMN IF NOT EXISTS run_id     INT        NOT NULL;
ALTER TABLE slots ADD COLUMN IF NOT EXISTS class_code VARCHAR(3) NOT NULL;
ALTER TABLE slots ADD COLUMN IF NOT EXISTS coach_no   VARCHAR(4) NOT NULL;
ALTER TABLE slots ADD COLUMN IF NOT EXISTS berth_no   INT        NOT NULL CHECK (berth_no >= 1);
ALTER TABLE slots ADD COLUMN IF NOT EXISTS berth_type VARCHAR(4) NOT NULL
  CHECK (berth_type IN ('LB','MB','UB','SL','SU','WS','MS','AS'));
ALTER TABLE slots ADD COLUMN IF NOT EXISTS quota      VARCHAR(7) NOT NULL
  CHECK (quota IN ('GENERAL','TATKAL'));

-- ===== users: demo login columns (engine columns unchanged) =====
ALTER TABLE users ADD COLUMN IF NOT EXISTS email            VARCHAR(254);
ALTER TABLE users ADD COLUMN IF NOT EXISTS mobile           VARCHAR(15);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash    VARCHAR(100);   -- BCrypt hash only, never a password
ALTER TABLE users ADD COLUMN IF NOT EXISTS aadhaar_verified BOOLEAN NOT NULL DEFAULT false;  -- simulated flag
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at       TIMESTAMP DEFAULT now();
CREATE UNIQUE INDEX IF NOT EXISTS users_email_unique ON users (lower(email)) WHERE email IS NOT NULL;

CREATE TABLE IF NOT EXISTS sessions (
  token_hash VARCHAR(64) PRIMARY KEY,              -- SHA-256 of the token; the token itself is never stored
  user_id    INT       NOT NULL REFERENCES users(id),
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  expires_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS sessions_user_idx ON sessions (user_id);

-- ===== reservations (one per PNR) =====
CREATE SEQUENCE IF NOT EXISTS pnr_seq START WITH 1000000001 MINVALUE 1000000001 MAXVALUE 9999999999;
CREATE TABLE IF NOT EXISTS reservations (
  id              SERIAL PRIMARY KEY,
  pnr             CHAR(10)   NOT NULL UNIQUE CHECK (pnr ~ '^[0-9]{10}$'),
  user_id         INT        NOT NULL REFERENCES users(id),
  run_id          INT        NOT NULL,
  from_station_id INT        NOT NULL REFERENCES stations(id),
  to_station_id   INT        NOT NULL REFERENCES stations(id),
  class_code      VARCHAR(3) NOT NULL,
  quota           VARCHAR(7) NOT NULL CHECK (quota IN ('GENERAL','TATKAL')),
  status          VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED'
                  CHECK (status IN ('CONFIRMED','PARTIALLY_CANCELLED','CANCELLED')),
  base_fare       NUMERIC(10,2) NOT NULL CHECK (base_fare >= 0),
  tatkal_charge   NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (tatkal_charge >= 0),
  total_fare      NUMERIC(10,2) NOT NULL CHECK (total_fare >= 0),
  created_at      TIMESTAMP NOT NULL DEFAULT now(),
  cancelled_at    TIMESTAMP,
  CONSTRAINT reservations_run_class_fk FOREIGN KEY (run_id, class_code) REFERENCES run_classes(run_id, class_code),
  CONSTRAINT reservations_from_to_differ CHECK (from_station_id <> to_station_id),
  CONSTRAINT reservations_no_tatkal_1a CHECK (NOT (class_code = '1A' AND quota = 'TATKAL'))
);
CREATE INDEX IF NOT EXISTS reservations_user_idx ON reservations (user_id);

-- ===== bookings: one row per passenger-berth (engine columns + passenger columns) =====
CREATE TABLE IF NOT EXISTS bookings (
  id           SERIAL PRIMARY KEY,
  slot_id      INT NOT NULL REFERENCES slots(id),
  user_id      INT NOT NULL REFERENCES users(id),
  status       VARCHAR(10) NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED','CANCELLED')),
  created_at   TIMESTAMP NOT NULL DEFAULT now(),
  cancelled_at TIMESTAMP
);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS reservation_id   INT REFERENCES reservations(id);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS passenger_name   VARCHAR(100);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS age              INT CHECK (age BETWEEN 1 AND 120);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS gender           CHAR(1) CHECK (gender IN ('M','F','T'));
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS berth_preference VARCHAR(4)
  CHECK (berth_preference IN ('LB','MB','UB','SL','SU','WS','MS','AS','NONE'));
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS fare_share       NUMERIC(10,2) CHECK (fare_share >= 0);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS refund_amount    NUMERIC(10,2) CHECK (refund_amount >= 0);
CREATE INDEX IF NOT EXISTS bookings_reservation_idx ON bookings (reservation_id);

-- THE correctness guarantee: at most one CONFIRMED booking per berth. Partial, so cancelled rows
-- (kept as history) never block rebooking. A plain UNIQUE(slot_id) would be WRONG.
CREATE UNIQUE INDEX IF NOT EXISTS one_confirmed_booking_per_slot
  ON bookings (slot_id) WHERE status = 'CONFIRMED';

-- ===== Short-lived and support tables =====
CREATE TABLE IF NOT EXISTS booking_drafts (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         INT        NOT NULL REFERENCES users(id),
  run_id          INT        NOT NULL REFERENCES train_runs(id),
  class_code      VARCHAR(3) NOT NULL,
  quota           VARCHAR(7) NOT NULL CHECK (quota IN ('GENERAL','TATKAL')),
  from_station_id INT        NOT NULL REFERENCES stations(id),
  to_station_id   INT        NOT NULL REFERENCES stations(id),
  slot_ids        INT[]      NOT NULL,          -- allowed only here: short-lived scratch row
  passengers      JSONB      NOT NULL,
  fare            JSONB      NOT NULL,
  expires_at      TIMESTAMP  NOT NULL,
  state           VARCHAR(8) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','PAID','FAILED','EXPIRED')),
  created_at      TIMESTAMP  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
  key        VARCHAR(64) NOT NULL,
  user_id    INT         NOT NULL REFERENCES users(id),
  endpoint   VARCHAR(40) NOT NULL,
  response   JSONB,
  created_at TIMESTAMP   NOT NULL DEFAULT now(),
  PRIMARY KEY (key, user_id, endpoint)
);

CREATE TABLE IF NOT EXISTS config_rules (
  key   VARCHAR(60) PRIMARY KEY,
  value TEXT NOT NULL,
  note  TEXT
);

CREATE TABLE IF NOT EXISTS tatkal_demo_override (
  run_id     INT PRIMARY KEY REFERENCES train_runs(id),
  open_until TIMESTAMP NOT NULL                    -- demo mode only
);

-- ===== Multi-column constraints on slots, added only if missing (keeps the file re-runnable) =====
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'slots_run_train_fk') THEN
    ALTER TABLE slots ADD CONSTRAINT slots_run_train_fk
      FOREIGN KEY (run_id, resource_id) REFERENCES train_runs(id, train_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'slots_run_class_fk') THEN
    ALTER TABLE slots ADD CONSTRAINT slots_run_class_fk
      FOREIGN KEY (run_id, class_code) REFERENCES run_classes(run_id, class_code);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'slots_berth_unique') THEN
    ALTER TABLE slots ADD CONSTRAINT slots_berth_unique UNIQUE (run_id, class_code, coach_no, berth_no);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'slots_no_tatkal_1a') THEN
    ALTER TABLE slots ADD CONSTRAINT slots_no_tatkal_1a CHECK (NOT (class_code = '1A' AND quota = 'TATKAL'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'bookings_passenger_complete') THEN
    ALTER TABLE bookings ADD CONSTRAINT bookings_passenger_complete
      CHECK (reservation_id IS NULL OR (passenger_name IS NOT NULL AND age IS NOT NULL AND gender IS NOT NULL));
  END IF;
END $$;

-- Speeds up the allocation query (free berths of one run/class/quota in id order).
CREATE INDEX IF NOT EXISTS slots_alloc_idx ON slots (run_id, class_code, quota, id) WHERE status = 'AVAILABLE';

COMMIT;
