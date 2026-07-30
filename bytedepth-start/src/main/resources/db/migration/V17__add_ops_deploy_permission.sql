INSERT IGNORE INTO `permission` (`code`, `description`, `module`, `created_at`)
VALUES ('ops:deploy:execute', '执行受控部署', 'admin', NOW());

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p ON p.code = 'ops:deploy:execute'
WHERE r.name = 'ADMIN';
