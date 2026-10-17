package manfred.bytedepth.integration;

import java.util.List;

final class AnnotationMigrationFixture {
    static final List<String> TABLES =
            List.of("post_annotation", "post_content_revision", "annotation_request", "annotation_placement");
    static final List<String> MIGRATIONS = List.of(
            "V20__add_post_annotation.sql",
            "V21__upgrade_post_annotations_for_visibility.sql",
            "V22__add_annotation_deleted_flag.sql",
            "V29__versioned_annotation_anchors.sql");

    private AnnotationMigrationFixture() {}

    static String namespacedSql(String sql, String prefix) {
        // Index names are scoped to their table in MySQL. Leave those names intact:
        // prefixing a table-name substring inside an index can exceed 64 characters.
        for (String table : TABLES) sql = sql.replaceAll("\\b" + table + "\\b", prefix + "_" + table);
        return sql;
    }
}
