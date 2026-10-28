-- Baseline schema. NOTE: no unique index on bookings yet (added in Stage 4).
DROP TABLE IF EXISTS bookings;
DROP TABLE IF EXISTS slots;
DROP TABLE IF EXISTS resources;
DROP TABLE IF EXISTS users;

CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL);
CREATE TABLE resources (id SERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL);

CREATE TABLE slots (
  id SERIAL PRIMARY KEY,
  resource_id INT NOT NULL REFERENCES resources(id),
  start_time TIMESTAMP NOT NULL,
  end_time TIMESTAMP NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'AVAILABLE'
    CHECK (status IN ('AVAILABLE','BOOKED')),
  held_by INT REFERENCES users(id),
  held_until TIMESTAMP
);

CREATE TABLE bookings (
  id SERIAL PRIMARY KEY,
  slot_id INT NOT NULL REFERENCES slots(id),
  user_id INT NOT NULL REFERENCES users(id),
  status VARCHAR(10) NOT NULL DEFAULT 'CONFIRMED'
    CHECK (status IN ('CONFIRMED','CANCELLED')),
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  cancelled_at TIMESTAMP
);