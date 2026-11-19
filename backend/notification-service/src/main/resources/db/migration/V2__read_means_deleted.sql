-- Notifications keep only what hasn't been read: opening a notification (or "Mark all read")
-- deletes it. Read ones from before this change go now, and the read flag is no longer needed.
-- ntf_received_events stays: it still makes a repeated delivery of an event a no-op.

DELETE FROM ntf_notifications WHERE read_at IS NOT NULL;

DROP INDEX ix_ntf_notifications_unread;
ALTER TABLE ntf_notifications DROP COLUMN read_at;

-- Reading is deleting. The row-level security policy (USING) still limits it to the
-- recipient's own notifications in their own workshop.
GRANT DELETE ON ntf_notifications TO ${app_role};
