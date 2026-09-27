DELETE FROM `role_permission`
WHERE `permission_id` = (SELECT `id` FROM `permission` WHERE `code` = 'ops:deploy:execute');

DELETE FROM `permission`
WHERE code = 'ops:deploy:execute';
