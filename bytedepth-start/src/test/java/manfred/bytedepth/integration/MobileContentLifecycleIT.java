package manfred.bytedepth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import manfred.bytedepth.domain.search.PostSearchDoc;
import org.junit.jupiter.api.Test;

class MobileContentLifecycleIT extends MobileIntegrationFixtures {
    @Test
    void databasePublicationRevisionAndWithdrawalControlContentAndConditionalRequests() throws Exception {
        var post = draft(
                "目标中文😀\n\n![fixture](https://images.invalid/mobile-it.png)\n\n<script>alert('private')</script>");
        String route = API + "/posts/" + post.getId();
        perform(get(route + "/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.identity").doesNotExist())
                .andExpect(jsonPath("$.title").doesNotExist());
        perform(get(route + "/content"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_UNAVAILABLE"));
        post.publish();
        posts.save(post);
        var response = perform(get(route + "/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(post.getId().toString()))
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.resources[0].url").value("https://images.invalid/mobile-it.png"))
                .andExpect(jsonPath("$.resources[0].required").value(true))
                .andExpect(jsonPath("$.resources[0].role").value("image"))
                .andExpect(jsonPath("$.resources[0].mediaType").isEmpty())
                .andExpect(jsonPath("$.resources[0].sha256").isEmpty())
                .andReturn();
        var content = body(response);
        assertThat(content.get("html").asString()).doesNotContain("<script");
        assertThat(content.get("document").get("text").asString()).contains("目标中文😀");
        for (String field : java.util.List.of("session", "csrf", "ratings", "comments", "annotations", "password"))
            assertThat(content.has(field)).as("public body excludes %s", field).isFalse();
        String etag = response.getResponse().getHeader("ETag");
        assertThat(etag).isEqualTo("\"" + content.get("representationHash").asString() + "\"");
        perform(get(route + "/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.representationHash")
                        .value(content.get("representationHash").asString()));
        perform(get(route + "/content").header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
        updatePost.execute(post.getId(), "明确的新标题", "目标中文😀\n\n更新正文");
        var refreshed = perform(get(route + "/content").header("If-None-Match", etag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("明确的新标题"))
                .andReturn();
        assertThat(body(refreshed).get("contentVersion").asInt())
                .isGreaterThan(content.get("contentVersion").asInt());
        assertThat(refreshed.getResponse().getHeader("ETag")).isNotEqualTo(etag);
        jdbc.update("UPDATE post SET status='DRAFT' WHERE id=?", post.getId());
        perform(get(route + "/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.identity").doesNotExist());
        perform(get(route + "/content")
                        .header("If-None-Match", refreshed.getResponse().getHeader("ETag")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_UNAVAILABLE"));
        assertNoReadingRecords(post.getId());
    }

    @Test
    void concurrentWithdrawalWinsBeforeOldEtagCanProduce304() throws Exception {
        var post = published("并发撤回正文");
        String route = API + "/posts/" + post.getId() + "/content";
        String etag = perform(get(route))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("ETag");
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var writerConnection = new AtomicLong();
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        var observedQuery = new AtomicReference<LockingQuery>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var withdrawal = executor.submit(() -> transactions.withLockedPost(post.getId(), locked -> {
                    writerConnection.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                    held.countDown();
                    await(release);
                    locked.delete();
                    posts.save(locked);
                    return null;
                }));
                assertTrue(held.await(10, TimeUnit.SECONDS));
                var reader = executor.submit(
                        () -> perform(get(route).header("If-None-Match", etag)).andReturn());
                // Observe the actual server-side locking SELECT before allowing the holder to commit.
                // If the reader stops taking the lock, it cannot satisfy this condition even if it returns 404 later.
                org.awaitility.Awaitility.await()
                        .atMost(Duration.ofSeconds(10))
                        .pollInterval(Duration.ofMillis(20))
                        .untilAsserted(() -> {
                            var queries = contendingPostQueries(database, writerConnection.get(), post.getId());
                            assertThat(queries)
                                    .as("Active target locking query in Host schema")
                                    .hasSize(1);
                            observedQuery.set(queries.getFirst());
                        });
                assertFalse(
                        reader.isDone(), "An observed contending read must remain blocked until withdrawal commits");
                System.out.printf(
                        "Mobile withdrawal lock contention: holder=%d, reader=%s%n",
                        writerConnection.get(), observedQuery.get());
                release.countDown();
                withdrawal.get(10, TimeUnit.SECONDS);
                var result = reader.get(10, TimeUnit.SECONDS);
                assertThat(result.getResponse().getStatus()).isEqualTo(404);
                assertThat(body(result).get("code").asString()).isEqualTo("POST_UNAVAILABLE");
            } finally {
                release.countDown();
            }
        }
        assertNoReadingRecords(post.getId());
    }

    @Test
    void mobileSearchRechecksRealIndexVisibilityCurrentSlugAndPlainText() throws Exception {
        String term = "mobilesearch" + UUID.randomUUID().toString().replace("-", "");
        var available = published("公开搜索正文");
        var withdrawn = published("撤回搜索正文");
        var orphan = draft("待删除搜索正文");
        search.indexAll(List.of(
                PostSearchDoc.builder()
                        .id(available.getId())
                        .slug(available.getSlug())
                        .title("<em>" + term + "</em>")
                        .content("<strong>native " + term + "</strong> &amp; safe")
                        .build(),
                PostSearchDoc.builder()
                        .id(withdrawn.getId())
                        .slug(withdrawn.getSlug())
                        .title(term)
                        .content("withdrawn-private-marker")
                        .build(),
                PostSearchDoc.builder()
                        .id(orphan.getId())
                        .slug(orphan.getSlug())
                        .title(term)
                        .content("orphan-private-marker")
                        .build()));
        assertThat(search.search(term, 1, 10).getHits())
                .extracting(PostSearchDoc::getId)
                .containsExactlyInAnyOrder(available.getId(), withdrawn.getId(), orphan.getId());
        String currentSlug = available.getSlug() + "-current";
        jdbc.update("UPDATE post SET slug=? WHERE id=?", currentSlug, available.getId());
        jdbc.update("UPDATE post SET status='DRAFT' WHERE id=?", withdrawn.getId());
        jdbc.update("DELETE FROM post WHERE id=?", orphan.getId());
        // Keep stale index documents deliberately: the mobile API must re-read the real database.
        assertThat(search.search(term, 1, 10).getHits())
                .extracting(PostSearchDoc::getSlug)
                .contains(available.getSlug(), withdrawn.getSlug(), orphan.getSlug());
        var result = perform(get(API + "/search").param("query", " " + term + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(available.getId().toString()))
                .andExpect(jsonPath("$.items[0].slug").value(currentSlug))
                .andExpect(jsonPath("$.items[0].title").value(term))
                .andExpect(jsonPath("$.items[0].snippet").value("native " + term + " & safe"))
                .andReturn();
        assertThat(body(result).get("items").get(0).get("id").isString()).isTrue();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("<em>", "<strong>", "private-marker");
        jdbc.update("UPDATE post SET status='DRAFT' WHERE id=?", available.getId());
        perform(get(API + "/search").param("query", term))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));
        assertThat(search.search(term, 1, 10).getHits()).hasSize(3);
    }

    private List<LockingQuery> contendingPostQueries(String database, long writerConnection, long post) {
        // FULL avoids truncating Info; same-user connections are visible without a PROCESS grant.
        // Capture only our target SQL, never dump other connections' statements into test output.
        var sql = Pattern.compile(
                "(?is).*\\bFROM\\s+`?post`?\\s+WHERE\\s+`?id`?\\s*=\\s*'?" + post + "'?\\s+FOR\\s+UPDATE\\s*;?\\s*");
        return jdbc.queryForList("SHOW FULL PROCESSLIST").stream()
                .filter(process -> database.equals(process.get("db")))
                .filter(process -> ((Number) process.get("Id")).longValue() != writerConnection)
                .filter(process -> List.of("Query", "Execute").contains(process.get("Command")))
                .filter(process -> process.get("Info") instanceof String info
                        && sql.matcher(info).matches())
                .map(process -> new LockingQuery(
                        ((Number) process.get("Id")).longValue(),
                        String.valueOf(process.get("Command")),
                        String.valueOf(process.get("State")),
                        (String) process.get("Info")))
                .toList();
    }

    private record LockingQuery(long connectionId, String command, String state, String sql) {}

    private void assertNoReadingRecords(long post) {
        assertThat(stats.getCount(post)).isZero();
        for (String table : java.util.List.of("post_view_log", "post_reading_event", "user_post_reading_history"))
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE post_id=?", Long.class, post))
                    .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM page_stats WHERE path=?", Long.class, "/posts/" + post))
                .isZero();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Coordination timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
