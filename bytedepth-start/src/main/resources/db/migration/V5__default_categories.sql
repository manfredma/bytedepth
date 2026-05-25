-- 预置技术博客分类（参考 InfoQ / Martin Fowler 风格）
INSERT IGNORE INTO `category` (name, slug, parent_id) VALUES
    ('未分类', 'uncategorized', NULL),
    ('架构设计', 'architecture', NULL),
    ('分布式系统', 'distributed', NULL),
    ('微服务', 'microservices', NULL),
    ('DevOps', 'devops', NULL),
    ('数据工程', 'data', NULL),
    ('编程语言', 'language', NULL),
    ('性能优化', 'performance', NULL),
    ('工程实践', 'engineering', NULL),
    ('源码解析', 'source-code', NULL),
    ('读书笔记', 'reading', NULL);

-- 已有未归类文章统一设为「未分类」
UPDATE `post`
SET category_id = (SELECT id FROM `category` WHERE slug = 'uncategorized' LIMIT 1)
WHERE category_id IS NULL;
