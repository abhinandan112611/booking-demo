INSERT INTO users (name) SELECT 'User ' || g FROM generate_series(1, 50) g;
INSERT INTO resources (name) VALUES ('Lab Room 1');
INSERT INTO slots (resource_id, start_time, end_time)
SELECT 1,
       timestamp '2026-11-02 09:00' + (g - 1) * interval '1 hour',
       timestamp '2026-11-02 10:00' + (g - 1) * interval '1 hour'
FROM generate_series(1, 6) g;