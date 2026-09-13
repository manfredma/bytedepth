-- V5 already creates computer-fundamentals.  Keep only V8's new category so
-- MySQL does not emit a duplicate-key warning during a clean migration.
INSERT INTO `category` (name, slug, parent_id) VALUES
    ('开发框架', 'framework', NULL);
