package manfred.bytedepth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import manfred.bytedepth.app.annotation.CreateAnnotationRequest;
import manfred.bytedepth.app.post.PostWriteTransaction;
import manfred.bytedepth.app.post.command.UpdatePostCmdExe;
import manfred.bytedepth.app.post.document.ArticleRevisionService;
import manfred.bytedepth.domain.annotation.AnnotationAnchor;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.document.SegmentSpan;
import manfred.bytedepth.infrastructure.redis.RedisKeyNamespace;
import manfred.bytedepth.infrastructure.stats.RedisStatsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Uses only the Host runner's isolated staging-it schema, Redis database and namespaces. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("staging-it")
abstract class MobileIntegrationFixtures {
    static final String API = "/api/mobile/v1";
    static final String PASSWORD = "MobileTestPassword1";
    static final AtomicInteger NEXT_IP = new AtomicInteger();
    final String ip = "192.0.2." + NEXT_IP.incrementAndGet();
    final List<Long> ownedPosts = new ArrayList<>();
    final List<Account> ownedAccounts = new ArrayList<>();
    final Set<String> ownedSessions = new HashSet<>();
    final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PostRepository posts;

    @Autowired
    ArticleRevisionService revisions;

    @Autowired
    PostWriteTransaction transactions;

    @Autowired
    UpdatePostCmdExe updatePost;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    SessionRepository<?> sessions;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    RedisKeyNamespace keys;

    @Autowired
    RedisStatsService stats;

    @Autowired
    Environment environment;

    @BeforeEach
    void requireHostIsolatedResources() {
        // Do not silently fall back to application.yml's production resources.
        for (String key : List.of(
                "BYTEDEPTH_STAGING_IT_DATASOURCE_URL",
                "BYTEDEPTH_STAGING_IT_REDIS_DATABASE",
                "BYTEDEPTH_STAGING_IT_REDIS_SESSION_NAMESPACE",
                "BYTEDEPTH_STAGING_IT_REDIS_KEY_NAMESPACE")) {
            assertThat(environment.getProperty(key))
                    .as("Host staging resource: %s", key)
                    .isNotBlank();
        }
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo(environment.getProperty("BYTEDEPTH_STAGING_IT_DATASOURCE_URL"));
        assertThat(environment.getProperty("spring.data.redis.database"))
                .isEqualTo(environment.getProperty("BYTEDEPTH_STAGING_IT_REDIS_DATABASE"));
        assertThat(environment.getProperty("spring.session.data.redis.namespace"))
                .isEqualTo(environment.getProperty("BYTEDEPTH_STAGING_IT_REDIS_SESSION_NAMESPACE"));
        assertThat(environment.getProperty("bytedepth.redis.key-namespace"))
                .isEqualTo(environment.getProperty("BYTEDEPTH_STAGING_IT_REDIS_KEY_NAMESPACE"));
    }

    void expireSession(String id) {
        expireSession(sessions, id);
    }

    private static <S extends org.springframework.session.Session> void expireSession(
            SessionRepository<S> repository, String id) {
        S persisted = repository.findById(id);
        assertThat(persisted).isNotNull();
        persisted.setLastAccessedTime(java.time.Instant.now()
                .minus(persisted.getMaxInactiveInterval())
                .minusSeconds(1));
        repository.save(persisted);
    }

    Account account(boolean comments) {
        String username = "mobile-it-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO `user` (username,password,status,created_at,updated_at) VALUES (?,?,'ACTIVE',NOW(),NOW())",
                username,
                encoder.encode(PASSWORD));
        long id = jdbc.queryForObject("SELECT id FROM `user` WHERE username=?", Long.class, username);
        var account = new Account(id, username);
        ownedAccounts.add(account);
        if (comments) {
            jdbc.update("INSERT INTO `role` (name,created_at) VALUES (?,NOW())", username);
            long role = jdbc.queryForObject("SELECT id FROM `role` WHERE name=?", Long.class, username);
            jdbc.update(
                    "INSERT INTO role_permission (role_id,permission_id) SELECT ?,id FROM permission WHERE code='blog:comment:create'",
                    role);
            assertThat(jdbc.update("INSERT INTO user_role (user_id,role_id) VALUES (?,?)", id, role))
                    .isEqualTo(1);
        }
        return account;
    }

    Post draft(String body) {
        var post = posts.save(Post.create("移动集成测试", body, account(false).id(), "mobile-it-" + UUID.randomUUID()));
        ownedPosts.add(post.getId());
        return post;
    }

    Post published(String body) {
        var post = draft(body);
        post.publish();
        return posts.save(post);
    }

    MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder request) {
        return request.header("X-Real-IP", ip);
    }

    MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, Session session) {
        return request(request).header("X-App-Session", session.id()).header(session.header(), session.token());
    }

    org.springframework.test.web.servlet.ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andDo(result -> {
            String header = result.getResponse().getHeader("X-App-Session");
            if (header != null && !header.isBlank()) ownedSessions.add(header);
            var cookie = result.getResponse().getCookie("SESSION");
            if (cookie != null && !cookie.getValue().isBlank())
                ownedSessions.add(new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8));
        });
    }

    Session csrf(String id) throws Exception {
        var request = request(get(API + "/csrf"));
        if (id != null) request.header("X-App-Session", id);
        var result = perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        String returned = result.getResponse().getHeader("X-App-Session");
        String current = returned == null ? id : returned;
        assertThat(current).isNotBlank();
        ownedSessions.add(current);
        var body = body(result);
        return new Session(
                current, body.get("headerName").asString(), body.get("token").asString());
    }

    Session login(Account account) throws Exception {
        return login(account, csrf(null));
    }

    Session login(Account account, Session anonymous) throws Exception {
        var response = perform(authenticated(post(API + "/login"), anonymous)
                        .param("username", account.username())
                        .param("password", PASSWORD))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        String id = response.getResponse().getHeader("X-App-Session");
        assertThat(id).isNotBlank().isNotEqualTo(anonymous.id());
        ownedSessions.add(id);
        return csrf(id);
    }

    JsonNode body(MvcResult result) {
        return json.readTree(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
    }

    String payload(Object value) {
        return json.writeValueAsString(value);
    }

    CreateAnnotationRequest annotation(Post post, String quote, String note, AnnotationVisibility visibility) {
        var revision = revisions.current(post.getId()).revision();
        var document = revision.document();
        int index = document.text().indexOf(quote);
        assertThat(index).isGreaterThanOrEqualTo(0);
        int start = document.text().codePointCount(0, index);
        int end = start + quote.codePointCount(0, quote.length());
        int[] points = document.text().codePoints().toArray();
        var anchor = new AnnotationAnchor(
                1,
                revision.contentVersion(),
                document.projectionVersion(),
                document.digest(),
                quote,
                new String(points, Math.max(0, start - 128), Math.min(start, 128)),
                new String(points, end, Math.min(points.length - end, 128)),
                start,
                end,
                document.segments().stream()
                        .filter(s -> s.start() < end && s.end() > start)
                        .map(s -> new SegmentSpan(
                                s.id(), Math.max(start, s.start()) - s.start(), Math.min(end, s.end()) - s.start()))
                        .toList(),
                UUID.randomUUID());
        return new CreateAnnotationRequest(anchor, note, "yellow", visibility);
    }

    @AfterEach
    void cleanupOnlyOwnedFixtures() throws Exception {
        for (String session : ownedSessions) sessions.deleteById(session);
        for (long id : ownedPosts) {
            jdbc.update(
                    "DELETE FROM annotation_placement WHERE annotation_id IN (SELECT id FROM post_annotation WHERE post_id=?)",
                    id);
            for (String table : List.of(
                    "annotation_request",
                    "post_annotation",
                    "post_content_revision",
                    "post_rating",
                    "comment",
                    "post_tag",
                    "post_view_log",
                    "post_reading_event",
                    "user_post_reading_history")) jdbc.update("DELETE FROM " + table + " WHERE post_id=?", id);
            jdbc.update("DELETE FROM page_stats WHERE path=?", "/posts/" + id);
            jdbc.update("DELETE FROM post WHERE id=?", id);
            redis.delete(keys.key("pv:post:", Long.toString(id)));
        }
        for (var account : ownedAccounts) {
            jdbc.update(
                    "DELETE FROM role_permission WHERE role_id IN (SELECT id FROM `role` WHERE name=?)",
                    account.username());
            jdbc.update("DELETE FROM user_role WHERE user_id=?", account.id());
            jdbc.update("DELETE FROM `role` WHERE name=?", account.username());
            jdbc.update("DELETE FROM `user` WHERE id=?", account.id());
            deleteRateKey("login-username", account.username());
        }
        for (String rule : List.of("login-ip", "register-ip", "comment-rating-ip")) deleteRateKey(rule, ip);
    }

    private void deleteRateKey(String rule, String identity) throws Exception {
        String digest = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
        redis.delete(keys.key("bytedepth:rate-limit:", rule + ":" + digest));
    }

    record Account(long id, String username) {}

    record Session(String id, String header, String token) {}
}
