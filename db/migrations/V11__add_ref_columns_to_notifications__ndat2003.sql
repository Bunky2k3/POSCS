-- Adds ref_type/ref_id to notifications so an automatic reminder generator
-- (contract-expiring, ticket-SLA-due) can check "have I already notified
-- this user about this specific contract/ticket" before inserting a new
-- row, instead of re-notifying the same thing on every run. Both columns
-- are nullable since manually-created notifications (if any exist) have no
-- source record to reference.

INSERT INTO schema_migrations (version) VALUES ('V11__add_ref_columns_to_notifications__ndat2003');

ALTER TABLE `notifications`
  ADD COLUMN `ref_type` varchar(30) COLLATE utf8mb4_unicode_ci NULL AFTER `title`,
  ADD COLUMN `ref_id` int NULL AFTER `ref_type`;

CREATE INDEX `idx_notifications_ref` ON `notifications` (`user_id`, `ref_type`, `ref_id`);
