package manfred.bytedepth;

import manfred.bytedepth.domain.user.UserRepository;
import manfred.bytedepth.domain.user.UserStatus;
import manfred.bytedepth.infrastructure.search.MeiliSearchPostIndexer;
import manfred.bytedepth.infrastructure.stats.RedisStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E 测试：账户注册/审核/登录流程。
 * 使用 Testcontainers 启动真实 MySQL（Flyway 迁移自动执行）。
 * Redis 和 MeiliSearch 被 Mock，避免外部依赖。
 *
 * 本地 Colima 用户：Docker socket 在 ~/.colima/default/docker.sock，
 * 静态初始化块自动检测并配置，无需额外操作。
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)   // Docker 不可用/版本不兼容时跳过，不报错
@AutoConfigureMockMvc
class AccountFlowE2ETest {

    static {
        // 兼容 Colima Docker 运行时：自动配置 Docker socket 路径
        String home = System.getProperty("user.home");
        java.io.File colimaSocket = new java.io.File(home + "/.colima/default/docker.sock");
        if (colimaSocket.exists()) {
            String socketUri = "unix://" + colimaSocket.getAbsolutePath();
            System.setProperty("DOCKER_HOST", socketUri);
            System.setProperty("docker.host", socketUri);
        }
        // Colima Docker 要求最低 API 版本 1.44，docker-java shaded 版本用 "api.version" 键名
        // （反编译 TC 1.20.1 shaded DefaultDockerClientConfig$Builder 确认）
        System.setProperty("api.version", "1.44");
    }

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @MockBean private RedisStatsService redisStatsService;
    @MockBean private MeiliSearchPostIndexer meiliSearchPostIndexer;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // 链路 1：注册页正常加载
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void registerPage_loadsSuccessfully() throws Exception {
        mockMvc.perform(get("/register"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/register"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 链路 2：用户注册 → 账号处于 PENDING 状态
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void registration_createsUserInPendingStatus() throws Exception {
        mockMvc.perform(post("/register")
                .param("username", "testuser_e2e_1")
                .param("password", "password123")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?registered=1"));

        var user = userRepository.findByUsername("testuser_e2e_1");
        assertTrue(user.isPresent(), "用户应当被创建");
        assertEquals(UserStatus.PENDING, user.get().getStatus(), "新注册用户状态应为 PENDING");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 链路 3：重复注册同一用户名 → 重定向回注册页携带错误
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void duplicateUsername_redirectsWithError() throws Exception {
        // 第一次注册
        mockMvc.perform(post("/register")
                .param("username", "dup_e2e_user")
                .param("password", "pass123")
                .with(csrf()));

        // 第二次注册同一用户名
        mockMvc.perform(post("/register")
                .param("username", "dup_e2e_user")
                .param("password", "other_pass")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/register?error=*"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 链路 4：PENDING 用户尝试登录 → 被拒绝（DisabledException）
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void pendingUser_cannotLogin() throws Exception {
        // 先注册
        mockMvc.perform(post("/register")
                .param("username", "pending_login_e2e")
                .param("password", "pass123")
                .with(csrf()));

        // 尝试登录 → 应重定向到 /login?error
        mockMvc.perform(post("/login")
                .param("username", "pending_login_e2e")
                .param("password", "pass123")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 链路 5：匿名用户不能发表评论 → 重定向登录页
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void anonymous_cannotPostComment() throws Exception {
        mockMvc.perform(post("/posts/1/comments")
                .param("content", "Hello!")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(result -> {
                String location = result.getResponse().getRedirectedUrl();
                assertNotNull(location);
                assertTrue(location.endsWith("/login"), "Expected redirect to /login, got: " + location);
            });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 链路 6：管理员可以访问用户审核页
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "system:user:approve"})
    void adminUser_canAccessUserApprovalPage() throws Exception {
        mockMvc.perform(get("/admin/users"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/users/list"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 链路 7：管理员审核通过 → 用户状态变为 ACTIVE
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "system:user:approve"})
    void adminActivatesUser_statusBecomesActive() throws Exception {
        // 注册一个待审核用户
        mockMvc.perform(post("/register")
                .param("username", "activate_e2e_user")
                .param("password", "pass123")
                .with(csrf()));

        Long userId = userRepository.findByUsername("activate_e2e_user")
            .orElseThrow().getId();

        // 管理员激活
        mockMvc.perform(post("/admin/users/" + userId + "/activate")
                .with(csrf()))
            .andExpect(redirectedUrl("/admin/users"));

        var activated = userRepository.findByUsername("activate_e2e_user").orElseThrow();
        assertEquals(UserStatus.ACTIVE, activated.getStatus(), "激活后状态应为 ACTIVE");
    }
}
