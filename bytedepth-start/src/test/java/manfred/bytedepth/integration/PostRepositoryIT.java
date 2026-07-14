package manfred.bytedepth.integration;

import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.infrastructure.stats.RedisStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class PostRepositoryIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @MockBean
    private RedisStatsService redisStatsService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void save_newPost_assignsIdAndPersists() {
        Post post = Post.create("集成测试标题", "# 集成测试\n\n内容正文");
        Post saved = postRepository.save(post);

        assertNotNull(saved.getId());
        assertEquals(PostStatus.DRAFT, saved.getStatus());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void findById_existingPost_returnsPost() {
        Post post = Post.create("可查询文章", "查询测试内容");
        Post saved = postRepository.save(post);

        Optional<Post> found = postRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("可查询文章", found.get().getTitle());
        assertEquals(PostStatus.DRAFT, found.get().getStatus());
    }

    @Test
    void findById_nonExistent_returnsEmpty() {
        Optional<Post> found = postRepository.findById(99999L);
        assertTrue(found.isEmpty());
    }

    @Test
    void publish_persistsStatusChange() {
        Post post = Post.create("待发布文章", "正文");
        Post saved = postRepository.save(post);

        saved.publish();
        postRepository.save(saved);

        Post reloaded = postRepository.findById(saved.getId()).orElseThrow();
        assertEquals(PostStatus.PUBLISHED, reloaded.getStatus());
        assertNotNull(reloaded.getPublishedAt());
    }

    @Test
    void findPublished_returnsOnlyPublishedPosts() {
        Post draft = Post.create("草稿文章IT", "内容");
        postRepository.save(draft);

        Post toPublish = Post.create("已发布文章IT", "内容");
        Post saved = postRepository.save(toPublish);
        saved.publish();
        postRepository.save(saved);

        List<Post> published = postRepository.findPublished(1, 100);
        assertTrue(published.stream().allMatch(p -> p.getStatus() == PostStatus.PUBLISHED));
    }

    @Test
    void findsPublishedPostsByHotnessAndLatestExcludingIds() {
        Post first = publishPost("热门排序第一篇");
        Post second = publishPost("热门排序第二篇");
        Post third = publishPost("热门排序第三篇");

        jdbcTemplate.update("UPDATE post SET published_at = DATE_ADD(NOW(), INTERVAL -3 MINUTE) WHERE id = ?", first.getId());
        jdbcTemplate.update("UPDATE post SET published_at = DATE_ADD(NOW(), INTERVAL -2 MINUTE) WHERE id = ?", second.getId());
        jdbcTemplate.update("UPDATE post SET published_at = DATE_ADD(NOW(), INTERVAL -1 MINUTE) WHERE id = ?", third.getId());
        jdbcTemplate.update("INSERT INTO page_stats (path, pv_count, updated_at) VALUES (?, ?, NOW())",
                "/posts/" + first.getId(), 100);
        jdbcTemplate.update("INSERT INTO page_stats (path, pv_count, updated_at) VALUES (?, ?, NOW())",
                "/posts/" + second.getId(), 100);

        var hotPosts = postRepository.findPublishedByHotness(1, 3);
        assertThat(hotPosts)
                .extracting(row -> row.post().getId())
                .containsExactly(second.getId(), first.getId(), third.getId());
        assertThat(hotPosts.get(2).viewCount()).isZero();

        List<Post> latest = postRepository.findLatestPublishedExcluding(List.of(third.getId()), 3);
        assertThat(latest)
                .extracting(Post::getId)
                .doesNotContain(third.getId());
        assertThat(latest).hasSizeLessThanOrEqualTo(3)
                .isSortedAccordingTo((left, right) -> {
                    int publishedAtComparison = right.getPublishedAt().compareTo(left.getPublishedAt());
                    return publishedAtComparison != 0
                            ? publishedAtComparison
                            : right.getId().compareTo(left.getId());
                });
    }

    @Test
    void countAll_excludesDeletedPosts() {
        long before = postRepository.countAll();

        Post post = Post.create("待删除文章IT", "内容");
        Post saved = postRepository.save(post);
        saved.delete();
        postRepository.save(saved);

        assertEquals(before, postRepository.countAll());
    }

    @Test
    void publicPostsPage_withRealDb_returns200() throws Exception {
        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/posts/list"));
    }

    @Test
    void homePage_withRealDb_returns200() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/index"));
    }

    private Post publishPost(String title) {
        Post post = postRepository.save(Post.create(title, "内容", 1L, title));
        post.publish();
        return postRepository.save(post);
    }
}
