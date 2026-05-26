ALTER TABLE outbox_events ADD COLUMN status VARCHAR(20);

UPDATE outbox_events SET status = 'SENT' WHERE processed_at IS NOT NULL;
