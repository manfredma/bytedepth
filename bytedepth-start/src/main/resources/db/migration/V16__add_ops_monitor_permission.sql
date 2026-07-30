INSERT IGNORE INTO `permission` (`code`, `description`, `module`, `created_at`)
VALUES ('ops:monitor:view', '查看运维监控', 'admin', NOW());

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p ON p.code = 'ops:monitor:view'
WHERE r.name = 'ADMIN';
