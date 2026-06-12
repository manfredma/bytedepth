-- V11: 文章 URL Slug
-- 从标题提取英文 + 数字片段，生成可读 slug，替换 /posts/{id} 路由

-- 1. 新增可空列
ALTER TABLE post ADD COLUMN slug VARCHAR(255) NULL AFTER title;

-- 2. 从标题提取英文+数字片段，合并成 slug（MySQL 8.0 REGEXP_REPLACE）
UPDATE post
SET slug = TRIM(BOTH '-' FROM
    REGEXP_REPLACE(
        LOWER(REGEXP_REPLACE(title, '[^a-zA-Z0-9]+', '-')),
        '-{2,}', '-'
    )
);

-- 3. slug 空或过短（< 3 字符）时追加 id 后缀保证有意义
UPDATE post SET slug = CONCAT(IF(slug IS NULL OR slug = '', 'post', slug), '-', id)
WHERE slug IS NULL OR CHAR_LENGTH(slug) < 3;

-- 4. 处理重复 slug：第二次及之后出现的追加 -id 后缀
UPDATE post p1
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY slug ORDER BY id) AS rn
    FROM post
) ranked ON p1.id = ranked.id
SET p1.slug = CONCAT(p1.slug, '-', p1.id)
WHERE ranked.rn > 1;

-- 5. 改为 NOT NULL + UNIQUE 约束
ALTER TABLE post MODIFY COLUMN slug VARCHAR(255) NOT NULL;
ALTER TABLE post ADD UNIQUE KEY uk_post_slug (slug);
