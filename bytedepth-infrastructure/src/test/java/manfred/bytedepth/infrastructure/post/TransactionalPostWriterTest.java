package manfred.bytedepth.infrastructure.post;

import static org.assertj.core.api.Assertions.*;

import manfred.bytedepth.infrastructure.annotation.AnnotationDatabaseFixture;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TransactionalPostWriterTest {
    @Test
    void U36_lockReturnsTheFreshPostInsideAnActiveTransaction() throws Exception {
        try (var db = new AnnotationDatabaseFixture()) {
            var writer = new TransactionalPostWriter(db.mapper(PostMapper.class), db.transactions);
            String result = writer.withLockedPost(1L, post -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                        .isTrue();
                assertThat(post.getContentVersion()).isEqualTo(7);
                db.jdbc.update("UPDATE post SET content_version=8 WHERE id=1");
                return post.getContent();
            });
            assertThat(result).isEqualTo("原文");
            assertThat(db.jdbc.queryForObject("SELECT content_version FROM post WHERE id=1", Integer.class))
                    .isEqualTo(8);
        }
    }

    @Test
    void U37_snapshotWriteFailureRollsBackTheWholePostChange() throws Exception {
        try (var db = new AnnotationDatabaseFixture()) {
            var writer = new TransactionalPostWriter(db.mapper(PostMapper.class), db.transactions);
            assertThatThrownBy(() -> writer.withLockedPost(1L, post -> {
                        db.jdbc.update("UPDATE post SET content_version=8 WHERE id=1");
                        db.jdbc.update(
                                "INSERT INTO post_content_revision(post_id,content_version,markdown,baseline) VALUES(1,8,'changed',FALSE)");
                        return null;
                    }))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(db.jdbc.queryForObject("SELECT content_version FROM post WHERE id=1", Integer.class))
                    .isEqualTo(7);
            assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM post_content_revision", Integer.class))
                    .isZero();
            assertThatThrownBy(() -> writer.withLockedPost(99L, p -> null))
                    .isInstanceOf(manfred.bytedepth.domain.common.DomainException.class);
        }
    }
}
