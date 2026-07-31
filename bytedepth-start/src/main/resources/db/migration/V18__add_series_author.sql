ALTER TABLE `series`
  ADD COLUMN `author_id` BIGINT NULL AFTER `description`;

UPDATE `series` s
LEFT JOIN (
  SELECT u.id
  FROM `user` u
  JOIN `user_role` ur ON ur.user_id = u.id
  JOIN `role` r ON r.id = ur.role_id AND r.name = 'ADMIN'
  ORDER BY u.id
  LIMIT 1
) admin_owner ON TRUE
LEFT JOIN (
  SELECT id
  FROM `user`
  ORDER BY id
  LIMIT 1
) fallback_owner ON TRUE
SET s.author_id = COALESCE(admin_owner.id, fallback_owner.id)
WHERE s.author_id IS NULL;

ALTER TABLE `series`
  MODIFY COLUMN `author_id` BIGINT NOT NULL COMMENT '创建者用户 ID',
  ADD INDEX `idx_series_author_id` (`author_id`),
  ADD CONSTRAINT `fk_series_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`);
