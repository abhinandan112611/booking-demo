-- Stage 4: the correctness guarantee. A plain UNIQUE (slot_id) would be WRONG,
-- because cancelled rows would block rebooking forever.
CREATE UNIQUE INDEX one_confirmed_booking_per_slot
  ON bookings (slot_id) WHERE status = 'CONFIRMED';