package manfred.bytedepth.infrastructure.annotation;

import static org.assertj.core.api.Assertions.*;

import manfred.bytedepth.infrastructure.comment.*;
import org.junit.jupiter.api.Test;

class MobileInteractionPagingPersistenceTest {
    @Test
    void ownPageAndCountExcludeOtherAccountsAndNonPublicPostsAtSqlBoundary() throws Exception {
        try (var db = new AnnotationDatabaseFixture()) {
            db.jdbc.update(
                    "INSERT INTO post VALUES(2,'draft','draft','text','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1),(3,'deleted','deleted','text','DELETED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1)");
            for (int i = 1; i <= 5; i++)
                db.jdbc.update(
                        "INSERT INTO post_annotation(id,post_id,user_id,selected_text,color,visibility,start_offset,end_offset,created_at,deleted) VALUES(?,1,5,'quote','yellow','PRIVATE',0,5,CURRENT_TIMESTAMP,FALSE)",
                        i);
            db.jdbc.update(
                    "INSERT INTO post_annotation(id,post_id,user_id,color,visibility,deleted) VALUES(6,1,9,'yellow','PUBLIC',FALSE),(7,2,5,'yellow','PRIVATE',FALSE),(8,3,5,'yellow','PRIVATE',FALSE)");
            var repository = new MyBatisAnnotationRepository(db.mapper(PostAnnotationMapper.class));
            assertThat(repository.countOwnPublished(5L)).isEqualTo(5);
            assertThat(repository.findOwnPublished(5L, 2, 2))
                    .extracting(a -> a.id())
                    .containsExactly(3L, 2L);
            assertThat(repository.findOwnPublished(5L, 4, 2)).isEmpty();
        }
    }

    @Test
    void approvedPageResultAndCountNeverReturnPendingOrAnotherPost() throws Exception {
        try (var db = new AnnotationDatabaseFixture()) {
            db.jdbc.execute(
                    "CREATE TABLE comment(id BIGINT AUTO_INCREMENT PRIMARY KEY,post_id BIGINT,author_id BIGINT,author_name VARCHAR(40),content VARCHAR(100),status VARCHAR(20),created_at TIMESTAMP)");
            db.session.getConfiguration().addMapper(CommentMapper.class);
            for (int i = 1; i <= 5; i++)
                db.jdbc.update("INSERT INTO comment VALUES(?,1,5,'reader','approved','APPROVED',CURRENT_TIMESTAMP)", i);
            db.jdbc.update(
                    "INSERT INTO comment VALUES(6,1,5,'reader','pending','PENDING',CURRENT_TIMESTAMP),(7,2,5,'reader','other','APPROVED',CURRENT_TIMESTAMP)");
            var repository = new CommentRepositoryImpl(db.mapper(CommentMapper.class));
            assertThat(repository.countApprovedByPostId(1L)).isEqualTo(5);
            assertThat(repository.findApprovedByPostId(1L, 2, 2))
                    .extracting(c -> c.getId())
                    .containsExactly(3L, 4L);
            assertThat(repository.findApprovedByPostId(1L, 4, 2)).isEmpty();
        }
    }

    @Test
    void appAndWebVotesRemainSeparateAndAnInstallCanUpdateItsVote() throws Exception {
        try (var db = new AnnotationDatabaseFixture()) {
            db.jdbc.execute(
                    "CREATE TABLE post_rating(id BIGINT AUTO_INCREMENT PRIMARY KEY,post_id BIGINT,visitor_token VARCHAR(64),score INT,created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(post_id,visitor_token))");
            db.session.getConfiguration().addMapper(manfred.bytedepth.infrastructure.rating.PostRatingMapper.class);
            var ratings = new manfred.bytedepth.infrastructure.rating.PostRatingRepositoryImpl(
                    db.mapper(manfred.bytedepth.infrastructure.rating.PostRatingMapper.class));
            String uuid = "7d0b5345-18ee-46b4-9609-62513f6e3043";
            ratings.upsert(1L, uuid, 1);
            ratings.upsert(1L, "app:" + uuid, 3);
            ratings.upsert(1L, "app:" + uuid, 5);
            assertThat(ratings.getStats(1L).ratingCount()).isEqualTo(2);
            assertThat(ratings.getStats(1L).averageRating()).isEqualTo(3);
            assertThat(ratings.findScore(1L, uuid)).contains(1);
            assertThat(ratings.findScore(1L, "app:" + uuid)).contains(5);
        }
    }
}
