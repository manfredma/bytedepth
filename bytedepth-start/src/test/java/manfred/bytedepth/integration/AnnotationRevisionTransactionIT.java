package manfred.bytedepth.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.*;
import javax.sql.DataSource;
import manfred.bytedepth.app.annotation.*;
import manfred.bytedepth.app.post.*;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import manfred.bytedepth.domain.post.PostContentRevision;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class AnnotationRevisionTransactionIT extends AnnotationITSupport {
    @Autowired
    PostWriteTransaction transactions;

    @Autowired
    PostContentRevisionPort revisions;

    @Autowired
    AnnotationRequestPort requests;

    @Autowired
    DataSource dataSource;

    @Test
    void twoConnectionsSerializeBodySaveAndRejectTheOldPageInsideTheLock() throws Exception {
        var post = draft("目标句子");
        var selector = anchor(post, "目标句子");
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var writer = executor.submit(() -> transactions.withLockedPost(post.getId(), locked -> {
                held.countDown();
                await(release);
                locked.updateContent("标题更新", locked.getContent());
                posts.save(locked);
                current.capture(locked);
                return null;
            }));
            assertTrue(held.await(10, TimeUnit.SECONDS));
            var creator = executor.submit(() -> create.execute(
                    post.getId(),
                    null,
                    owner,
                    new CreateAnnotationRequest(selector, "不能跨版本写入", "yellow", AnnotationVisibility.PUBLIC)));
            try {
                assertThrows(TimeoutException.class, () -> creator.get(200, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            writer.get(10, TimeUnit.SECONDS);
            var failure = assertThrows(ExecutionException.class, () -> creator.get(10, TimeUnit.SECONDS));
            assertInstanceOf(AnnotationWriteConflictException.class, failure.getCause());
            assertEquals(
                    AnnotationWriteConflictException.Kind.STALE_REVISION,
                    ((AnnotationWriteConflictException) failure.getCause()).kind());
            assertTrue(annotations.findByPostId(post.getId()).isEmpty());
            assertEquals(2, posts.findById(post.getId()).orElseThrow().getContentVersion());
            assertEquals("目标句子", revisions.find(post.getId(), 2).orElseThrow().markdown());
        } finally {
            release.countDown();
        }
    }

    @Test
    void snapshotConstraintFailureRollsBackTheAlreadySavedBody() {
        var post = draft("目标句子");
        var source = current.current(post.getId()).revision();
        revisions.insert(new PostContentRevision(post.getId(), 2, source.markdown(), source.document(), false));
        assertThrows(DataIntegrityViolationException.class, () -> update.execute(post.getId(), "新标题", "新正文"));
        var actual = posts.findById(post.getId()).orElseThrow();
        assertEquals(1, actual.getContentVersion());
        assertEquals("目标句子", actual.getContent());
        assertEquals(source, revisions.find(post.getId(), 1).orElseThrow());
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM post_content_revision WHERE post_id=?", Integer.class, post.getId()));
    }

    @Test
    void requestUniqueIndexArbitratesConcurrentConnections() throws Exception {
        var post = draft("目标句子");
        var saved = annotate(post, "目标句子", AnnotationVisibility.PUBLIC);
        var scope = new AnnotationRequestPort.Scope(post.getId(), "visitor:" + owner, UUID.randomUUID());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> insert = () -> {
                await(start);
                try {
                    requests.insert(scope, "a".repeat(64), saved.annotation().id());
                    return true;
                } catch (DataIntegrityViolationException duplicate) {
                    return false;
                }
            };
            var first = executor.submit(insert);
            var second = executor.submit(insert);
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertTrue(requests.find(scope).isPresent());
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM annotation_request WHERE post_id=? AND request_id=?",
                            Integer.class,
                            post.getId(),
                            scope.requestId().toString()));
        }
    }

    @Test
    void flywayUpgradesNamespacedLegacyTablesWithoutChangingTheOldFields() throws Exception {
        // The Host Agent grants only the run-isolated schema. Exercise the actual
        // migrations with table names isolated again, never clean shared history.
        String prefix = "an_" + UUID.randomUUID().toString().replace("-", "");
        var directory = Files.createTempDirectory("annotation-migration-");
        var tables = java.util.List.of(
                "post_annotation", "post_content_revision", "annotation_request", "annotation_placement");
        try {
            for (String file : java.util.List.of(
                    "V20__add_post_annotation.sql",
                    "V21__upgrade_post_annotations_for_visibility.sql",
                    "V22__add_annotation_deleted_flag.sql",
                    "V29__versioned_annotation_anchors.sql")) {
                try (var input = getClass().getResourceAsStream("/db/migration/" + file)) {
                    assertNotNull(input);
                    String sql = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    for (String table : tables) sql = sql.replace(table, prefix + "_" + table);
                    Files.writeString(directory.resolve(file), sql);
                }
            }
            var config = Flyway.configure()
                    .dataSource(dataSource)
                    .table(prefix + "_history")
                    .locations("filesystem:" + directory)
                    .baselineOnMigrate(true)
                    .baselineVersion("19");
            config.target("22").load().migrate();
            jdbc.update(
                    "INSERT INTO " + prefix
                            + "_post_annotation (post_id,selected_text,annotation_text,color,start_offset,end_offset,created_at,owner_token_hash,visibility,deleted) VALUES (1,'原引用','保留评论','blue',2,5,NOW(),?,'PRIVATE',1)",
                    owner);
            var before = jdbc.queryForMap("SELECT * FROM " + prefix + "_post_annotation");
            var result = config.target("29").load().migrate();
            assertEquals(1, result.migrationsExecuted);
            var row = jdbc.queryForMap("SELECT * FROM " + prefix + "_post_annotation");
            for (var field : before.entrySet()) assertEquals(field.getValue(), row.get(field.getKey()), field.getKey());
            assertEquals("原引用", row.get("selected_text"));
            assertEquals("保留评论", row.get("annotation_text"));
            assertEquals("PRIVATE", row.get("visibility"));
            assertEquals(1, jdbc.queryForObject("SELECT deleted FROM " + prefix + "_post_annotation", Integer.class));
            assertNull(row.get("anchor_json"));
            assertNull(row.get("migration_baseline_version"));
            for (String table : tables.subList(1, tables.size()))
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + prefix + "_" + table, Integer.class));
        } finally {
            for (String table : tables) jdbc.execute("DROP TABLE IF EXISTS " + prefix + "_" + table);
            jdbc.execute("DROP TABLE IF EXISTS " + prefix + "_history");
            try (var files = Files.list(directory)) {
                for (var file : files.toList()) Files.delete(file);
            }
            Files.delete(directory);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Coordination timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
