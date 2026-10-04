package manfred.bytedepth.adapter.web.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties;
import manfred.bytedepth.adapter.web.security.SecurityConfig;
import manfred.bytedepth.adapter.web.security.SecurityMockMvcConfig;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import manfred.bytedepth.app.ops.OpsDatabasePort;
import manfred.bytedepth.app.ops.OpsDatabaseStatusDTO;
import manfred.bytedepth.app.ops.OpsMeiliSearchPort;
import manfred.bytedepth.app.ops.OpsMeiliSearchStatusDTO;
import manfred.bytedepth.app.ops.OpsOverviewQryExe;
import manfred.bytedepth.app.ops.OpsRedisPort;
import manfred.bytedepth.app.ops.OpsRedisStatusDTO;
import manfred.bytedepth.app.ops.OpsTableDataDTO;
import manfred.bytedepth.app.ops.OpsTableQryExe;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = AdminOpsController.class,
    excludeAutoConfiguration = DataSourceAutoConfiguration.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({
  SecurityConfig.class,
  ThymeleafSecurityHandlerConfig.class,
  SecurityMockMvcConfig.class,
  AdminOpsControllerTest.OpsQueryConfiguration.class
})
class AdminOpsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserDetailsService userDetailsService;
  @MockitoBean private VisitRequestFilter visitRequestFilter;
  @MockitoBean private PasswordEncoder passwordEncoder;
  @MockitoBean private DataSource dataSource;
  @MockitoBean private RateLimitPort rateLimitPort;
  @MockitoBean private RateLimitProperties rateLimitProperties;
  @MockitoBean private OpsDatabasePort databasePort;
  @MockitoBean private OpsRedisPort redisPort;
  @MockitoBean private OpsMeiliSearchPort meiliSearchPort;
  @MockitoBean private OpsTableQryExe tableQryExe;

  @Test
  @WithMockUser(authorities = {"admin:dashboard:view"})
  void endpoints_withoutOpsPermission_returnForbidden() throws Exception {
    mockMvc.perform(get("/admin/ops")).andExpect(status().isForbidden());
    mockMvc.perform(get("/admin/ops/api/overview")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = {"admin:dashboard:view", "ops:monitor:view"})
  void page_withOpsPermission_returnsDashboard() throws Exception {
    mockMvc
        .perform(get("/admin/ops"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/ops/dashboard"))
        .andExpect(content().string(not(containsString("部署发布版本"))));
  }

  @Test
  @WithMockUser(authorities = {"admin:dashboard:view", "ops:monitor:view"})
  void overview_withOpsPermission_returnsJson() throws Exception {
    givenOverview(true, true, true);

    mockMvc
        .perform(get("/admin/ops/api/overview"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.database.available").value(true))
        .andExpect(jsonPath("$.redis.usedMemoryHuman").value("12M"))
        .andExpect(jsonPath("$.meiliSearch.healthAvailable").value(true));
  }

  @Test
  @WithMockUser(authorities = {"admin:dashboard:view", "ops:monitor:view"})
  void overview_whenDependenciesAreDown_returnsSafeFixedDiagnostics() throws Exception {
    doThrow(
            new IllegalStateException(
                "jdbc:mysql://db.internal/bytedepth?password=mysql-secret\n"
                    + "at com.mysql.Driver.connect(Driver.java:42)"))
        .when(databasePort)
        .inspect();
    doThrow(
            new IllegalStateException(
                "redis://:redis-secret@cache.internal:6379\n"
                    + "at io.lettuce.core.RedisClient.connect(RedisClient.java:42)"))
        .when(redisPort)
        .inspect();
    doThrow(
            new IllegalStateException(
                "https://search.internal?apiKey=meili-secret\n"
                    + "at org.springframework.web.client.RestClient.get(RestClient.java:42)"))
        .when(meiliSearchPort)
        .inspect();

    mockMvc
        .perform(get("/admin/ops/api/overview"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.database.available").value(false))
        .andExpect(jsonPath("$.database.error").value("MySQL health check failed"))
        .andExpect(jsonPath("$.redis.available").value(false))
        .andExpect(jsonPath("$.redis.error").value("Redis health check failed"))
        .andExpect(jsonPath("$.meiliSearch.healthAvailable").value(false))
        .andExpect(jsonPath("$.meiliSearch.statsAvailable").value(false))
        .andExpect(jsonPath("$.meiliSearch.error").value("MeiliSearch health check failed"))
        .andExpect(content().string(not(containsString("internal"))))
        .andExpect(content().string(not(containsString("secret"))))
        .andExpect(content().string(not(containsString("Driver.java"))))
        .andExpect(content().string(not(containsString("RedisClient.java"))))
        .andExpect(content().string(not(containsString("RestClient.java"))));
  }

  @Test
  @WithMockUser(authorities = {"admin:dashboard:view", "ops:monitor:view"})
  void permittedTable_returnsWhitelistedData() throws Exception {
    when(tableQryExe.execute("post"))
        .thenReturn(
            new OpsTableDataDTO(
                "post", List.of("id", "title"), List.of(Map.of("id", 42, "title", "Operations"))));

    mockMvc
        .perform(get("/admin/ops/api/tables/post"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tableName").value("post"))
        .andExpect(jsonPath("$.columns[0]").value("id"))
        .andExpect(jsonPath("$.rows[0].title").value("Operations"));
  }

  @Test
  @WithMockUser(authorities = {"admin:dashboard:view", "ops:monitor:view"})
  void unsupportedTable_returnsBadRequest() throws Exception {
    when(tableQryExe.execute("not-allowed"))
        .thenThrow(new IllegalArgumentException("Unsupported operations table"));

    mockMvc.perform(get("/admin/ops/api/tables/not-allowed")).andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(authorities = {"admin:dashboard:view", "ops:monitor:view"})
  void tableFailure_returnsGenericResponseWithoutExceptionDetails() throws Exception {
    doThrow(new IllegalStateException("jdbc:mysql://db/bytedepth?password=db-secret"))
        .when(tableQryExe)
        .execute("post");

    mockMvc
        .perform(get("/admin/ops/api/tables/post"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().string(not(containsString("db-secret"))))
        .andExpect(content().string(not(containsString("jdbc:mysql"))));
  }

  private void givenOverview(
      boolean databaseAvailable, boolean redisAvailable, boolean meiliAvailable) {
    when(databasePort.inspect())
        .thenReturn(
            new OpsDatabaseStatusDTO(databaseAvailable, databaseAvailable ? "bytedepth" : null));
    when(redisPort.inspect())
        .thenReturn(
            new OpsRedisStatusDTO(redisAvailable, redisAvailable ? "12M" : null, 3, 7, 2, 5, 1));
    when(meiliSearchPort.inspect())
        .thenReturn(new OpsMeiliSearchStatusDTO(meiliAvailable, meiliAvailable));
  }

  @TestConfiguration
  static class OpsQueryConfiguration {

    @Bean
    OpsOverviewQryExe opsOverviewQryExe(
        OpsDatabasePort databasePort, OpsRedisPort redisPort, OpsMeiliSearchPort meiliSearchPort) {
      return new OpsOverviewQryExe(databasePort, redisPort, meiliSearchPort);
    }
  }
}
