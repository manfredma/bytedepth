ALTER TABLE `post`
  ADD COLUMN `featured_at` DATETIME NULL AFTER `featured`,
  ADD COLUMN `featured_reason` VARCHAR(500) NULL AFTER `featured_at`;

