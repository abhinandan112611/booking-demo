TRUNCATE bookings RESTART IDENTITY;
UPDATE slots SET status = 'AVAILABLE', held_by = NULL, held_until = NULL;