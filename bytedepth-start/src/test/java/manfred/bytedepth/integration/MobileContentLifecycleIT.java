package manfred.bytedepth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.concurrent.*;
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
        var reading = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var withdrawal = executor.submit(() -> transactions.withLockedPost(post.getId(), locked -> {
                    held.countDown();
                    await(release);
                    locked.delete();
                    posts.save(locked);
                    return null;
                }));
                assertTrue(held.await(10, TimeUnit.SECONDS));
                var reader = executor.submit(() -> {
                    reading.countDown();
                    return perform(get(route).header("If-None-Match", etag)).andReturn();
                });
                assertTrue(reading.await(10, TimeUnit.SECONDS));
                try {
                    assertThrows(TimeoutException.class, () -> reader.get(200, TimeUnit.MILLISECONDS));
                } finally {
                    release.countDown();
                }
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
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Coordination timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
