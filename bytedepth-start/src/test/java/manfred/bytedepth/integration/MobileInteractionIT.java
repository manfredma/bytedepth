package manfred.bytedepth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import manfred.bytedepth.app.annotation.CreateAnnotationRequest;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CsrfToken;

class MobileInteractionIT extends MobileIntegrationFixtures {
    @Test
    void installationCanChangeRatingWithoutIncreasingCountAndWebCookieIsAnotherIdentity() throws Exception {
        var post = published("匿名评分正文");
        String route = API + "/posts/" + post.getId();
        String installation = UUID.randomUUID().toString();
        String visitor = "app:" + installation;
        var anonymous = csrf(null);
        perform(request(put(route + "/rating"))
                        .header("X-App-Visitor", visitor)
                        .contentType("application/json")
                        .content("{\"score\":2}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        for (int score : List.of(2, 5)) {
            perform(authenticated(put(route + "/rating"), anonymous)
                            .header("X-App-Visitor", visitor)
                            .contentType("application/json")
                            .content(payload(Map.of("score", score))))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        perform(get(route + "/ratings").header("X-App-Visitor", visitor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingCount").value(1))
                .andExpect(jsonPath("$.visitorScore").value(5))
                .andExpect(jsonPath("$.averageScore").value(5));
        var loginPage =
                perform(request(get("/login"))).andExpect(status().isOk()).andReturn();
        var webCsrf = (CsrfToken) loginPage.getRequest().getAttribute(CsrfToken.class.getName());
        perform(request(post("/posts/" + post.getSlug() + "/rating"))
                        .cookie(
                                loginPage.getResponse().getCookie("SESSION"),
                                new Cookie("bytedepth-rating-visitor", installation))
                        .header(webCsrf.getHeaderName(), webCsrf.getToken())
                        .param("score", "1"))
                .andExpect(status().isFound());
        perform(get(route + "/ratings")
                        .header("X-App-Visitor", visitor)
                        .cookie(new Cookie("bytedepth-rating-visitor", installation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingCount").value(2))
                .andExpect(jsonPath("$.visitorScore").value(5))
                .andExpect(jsonPath("$.averageScore").value(3));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_rating WHERE post_id=?", Long.class, post.getId()))
                .isEqualTo(2);
        jdbc.update("UPDATE post SET status='DRAFT' WHERE id=?", post.getId());
        perform(authenticated(put(route + "/rating"), anonymous)
                        .header("X-App-Visitor", visitor)
                        .contentType("application/json")
                        .content("{\"score\":4}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_UNAVAILABLE"));
        assertThat(jdbc.queryForObject(
                        "SELECT score FROM post_rating WHERE post_id=? AND visitor_token=?",
                        Integer.class,
                        post.getId(),
                        visitor))
                .isEqualTo(5);
    }

    @Test
    void commentsRequirePermissionAndPagingCountsOnlyApprovedRows() throws Exception {
        var post = published("批准评论分页正文");
        String route = API + "/posts/" + post.getId() + "/comments";
        var limited = login(account(false));
        perform(authenticated(post(route), limited)
                        .contentType("application/json")
                        .content("{\"content\":\"forbidden\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        var author = account(true);
        var member = login(author);
        perform(authenticated(post(route), member)
                        .contentType("application/json")
                        .content("{\"content\":\"submitted-approved\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM comment WHERE post_id=?", String.class, post.getId()))
                .isEqualTo("APPROVED");
        perform(get(route))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].content").value("submitted-approved"));
        // Mapper ordering is created_at ASC, id ASC; the submitted row is deterministically last.
        jdbc.update("UPDATE comment SET created_at='2026-01-02 00:00:00' WHERE post_id=?", post.getId());
        for (int number = 1; number <= 5; number++) {
            jdbc.update(
                    "INSERT INTO comment (post_id,author_id,author_name,content,status,created_at) VALUES (?,?,?,?,'APPROVED','2026-01-01 00:00:00')",
                    post.getId(),
                    author.id(),
                    author.username(),
                    "approved-" + number);
        }
        for (String status : List.of("PENDING", "REJECTED")) {
            jdbc.update(
                    "INSERT INTO comment (post_id,author_id,author_name,content,status,created_at) VALUES (?,?,?,?,?,'2026-01-01 00:00:00')",
                    post.getId(),
                    author.id(),
                    author.username(),
                    status.toLowerCase(java.util.Locale.ROOT) + "-private-marker",
                    status);
        }
        for (int page = 1; page <= 3; page++) {
            var result = perform(
                            get(route).param("page", Integer.toString(page)).param("pageSize", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(6))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.hasNext").value(page < 3))
                    .andExpect(jsonPath("$.items[0].content").value("approved-" + (2 * page - 1)))
                    .andExpect(jsonPath("$.items[1].content")
                            .value(page == 3 ? "submitted-approved" : "approved-" + (2 * page)))
                    .andExpect(jsonPath("$.items[0].status").doesNotExist())
                    .andExpect(jsonPath("$.items[0].authorId").doesNotExist())
                    .andReturn();
            assertThat(body(result).get("items").size()).isEqualTo(2);
            assertThat(result.getResponse().getContentAsString()).doesNotContain("private-marker");
        }
        perform(get(route).param("page", "4").param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(6));
        jdbc.update("UPDATE post SET status='DRAFT' WHERE id=?", post.getId());
        perform(authenticated(post(route), member)
                        .contentType("application/json")
                        .content("{\"content\":\"after withdrawal\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comment WHERE post_id=?", Long.class, post.getId()))
                .isEqualTo(8);
    }

    @Test
    void annotationRetryLedgerPrivacyAndOwnershipSurviveRealPersistence() throws Exception {
        var post = published("开头😀\n\n目标句子");
        var owner = login(account(false));
        var other = login(account(false));
        var anonymous = csrf(null);
        String route = API + "/posts/" + post.getId() + "/annotations";
        var request = annotation(post, "目标句子", "private-owner-marker", AnnotationVisibility.PRIVATE);
        String body = payload(request);
        perform(authenticated(post(route), anonymous)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        var created = perform(authenticated(post(route), owner)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownedByCurrentVisitor").value(true))
                .andReturn();
        String id = body(created).get("id").asString();
        perform(authenticated(post(route), owner)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));
        perform(authenticated(post(route), owner)
                        .contentType("application/json")
                        .content(payload(new CreateAnnotationRequest(
                                request.anchor(), "changed payload", "yellow", AnnotationVisibility.PRIVATE))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_REUSED"));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM post_annotation WHERE post_id=?", Long.class, post.getId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM annotation_request WHERE post_id=?", Long.class, post.getId()))
                .isEqualTo(1);
        perform(get(route))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annotations").isEmpty());
        perform(authenticated(get(route), other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annotations").isEmpty());
        perform(authenticated(get(route), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annotations[0].id").value(id))
                .andExpect(jsonPath("$.annotations[0].annotationText").value("private-owner-marker"));
        perform(authenticated(get(API + "/me/annotations"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id));
        String update = "{\"annotationText\":\"edited\",\"visibility\":\"PUBLIC\"}";
        perform(authenticated(patch(route + "/" + id), other)
                        .contentType("application/json")
                        .content(update))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        perform(authenticated(delete(route + "/" + id), other)).andExpect(status().isForbidden());
        perform(authenticated(patch(route + "/" + id), owner)
                        .contentType("application/json")
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annotationText").value("edited"));
        perform(get(route))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annotations[0].id").value(id));
        perform(authenticated(delete(route + "/" + id), owner)).andExpect(status().isNoContent());
        perform(authenticated(post(route), owner)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("DELETED_REQUEST"));
        perform(authenticated(get(API + "/me/annotations"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void staleAndInvalidAnchorsCannotWriteAndEnvelopeMatchesCurrentRevision() throws Exception {
        var post = published("目标句子😀");
        var owner = login(account(false));
        String route = API + "/posts/" + post.getId();
        var old = annotation(post, "目标句子😀", "旧版草稿", AnnotationVisibility.PUBLIC);
        updatePost.execute(post.getId(), "新版本", "插入新段\n\n目标句子😀");
        perform(authenticated(post(route + "/annotations"), owner)
                        .contentType("application/json")
                        .content(payload(old)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_REVISION"));
        perform(authenticated(post(route + "/annotations"), owner)
                        .contentType("application/json")
                        .content(payload(
                                new CreateAnnotationRequest(null, "bad", "yellow", AnnotationVisibility.PUBLIC))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_ANCHOR"));
        var current = annotation(post, "目标句子😀", "当前评注", AnnotationVisibility.PUBLIC);
        perform(authenticated(post(route + "/annotations"), owner)
                        .contentType("application/json")
                        .content(payload(current)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.selectedText").value("目标句子😀"));
        var content =
                body(perform(get(route + "/content")).andExpect(status().isOk()).andReturn());
        perform(get(route + "/annotations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentVersion")
                        .value(content.get("contentVersion").asInt()))
                .andExpect(jsonPath("$.projectionVersion")
                        .value(content.get("projectionVersion").asInt()))
                .andExpect(jsonPath("$.digest").value(content.get("digest").asString()))
                .andExpect(jsonPath("$.annotations[0].placement.status").value("ANCHORED"));
        jdbc.update("UPDATE post SET status='DRAFT' WHERE id=?", post.getId());
        perform(authenticated(post(route + "/annotations"), owner)
                        .contentType("application/json")
                        .content(payload(current)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_UNAVAILABLE"));
        perform(authenticated(get(API + "/me/annotations"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }
}
