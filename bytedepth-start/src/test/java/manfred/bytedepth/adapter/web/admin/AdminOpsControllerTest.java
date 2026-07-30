package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.ops.OpsDatabaseStatusDTO;
import manfred.bytedepth.app.ops.OpsDatabasePort;
import manfred.bytedepth.app.ops.OpsMeiliSearchStatusDTO;
import manfred.bytedepth.app.ops.OpsMeiliSearchPort;
import manfred.bytedepth.app.ops.OpsOverviewQryExe;
import manfred.bytedepth.app.ops.OpsRedisPort;
import manfred.bytedepth.app.ops.OpsRedisStatusDTO;
import manfred.bytedepth.app.ops.OpsTableDataDTO;
import manfred.bytedepth.app.ops.OpsTableQryExe;
import manfred.bytedepth.adapter.web.security.SecurityConfig;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = AdminOpsController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
@Import({SecurityConfig.class, AdminOpsControllerTest.OpsQueryConfiguration.class})
class AdminOpsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private PasswordEncoder passwordEncoder;
    @MockBean
    private DataSource dataSource;
    @MockBean
    private OpsDatabasePort databasePort;
    @MockBean
    private OpsRedisPort redisPort;
    @MockBean
    private OpsMeiliSearchPort meiliSearchPort;
    @MockBean
    private OpsTableQryExe tableQryExe;

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void endpoints_withoutOpsPermission_returnForbidden() throws Exception {
        mockMvc.perform(get("/admin/ops"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/ops/api/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "ops:monitor:view"})
    void page_withOpsPermission_returnsDashboard() throws Exception {
        mockMvc.perform(get("/admin/ops"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/ops/dashboard"));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "ops:monitor:view"})
    void overview_withOpsPermission_returnsJson() throws Exception {
        givenOverview(true, true, true);

        mockMvc.perform(get("/admin/ops/api/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database.available").value(true))
                .andExpect(jsonPath("$.redis.usedMemoryHuman").value("12M"))
                .andExpect(jsonPath("$.meiliSearch.healthAvailable").value(true));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "ops:monitor:view"})
    void overview_whenOneDependencyIsDown_stillReturnsOk() throws Exception {
        when(databasePort.inspect()).thenReturn(new OpsDatabaseStatusDTO(true, "bytedepth"));
        doThrow(new IllegalStateException("Redis unavailable")).when(redisPort).inspect();
        when(meiliSearchPort.inspect()).thenReturn(new OpsMeiliSearchStatusDTO(true, true));

        mockMvc.perform(get("/admin/ops/api/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database.available").value(true))
                .andExpect(jsonPath("$.redis.available").value(false))
                .andExpect(jsonPath("$.meiliSearch.statsAvailable").value(true));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "ops:monitor:view"})
    void permittedTable_returnsWhitelistedData() throws Exception {
        when(tableQryExe.execute("post")).thenReturn(new OpsTableDataDTO(
                "post",
                List.of("id", "title"),
                List.of(Map.of("id", 42, "title", "Operations"))));

        mockMvc.perform(get("/admin/ops/api/tables/post"))
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

        mockMvc.perform(get("/admin/ops/api/tables/not-allowed"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "ops:monitor:view"})
    void tableFailure_returnsGenericResponseWithoutExceptionDetails() throws Exception {
        doThrow(new IllegalStateException("jdbc:mysql://db/bytedepth?password=db-secret"))
                .when(tableQryExe).execute("post");

        mockMvc.perform(get("/admin/ops/api/tables/post"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("db-secret"))))
                .andExpect(content().string(not(containsString("jdbc:mysql"))));
    }

    private void givenOverview(boolean databaseAvailable, boolean redisAvailable, boolean meiliAvailable) {
        when(databasePort.inspect()).thenReturn(new OpsDatabaseStatusDTO(
                databaseAvailable, databaseAvailable ? "bytedepth" : null));
        when(redisPort.inspect()).thenReturn(new OpsRedisStatusDTO(
                redisAvailable, redisAvailable ? "12M" : null, 3, 7, 2, 5, 1));
        when(meiliSearchPort.inspect()).thenReturn(new OpsMeiliSearchStatusDTO(
                meiliAvailable, meiliAvailable));
    }

    @TestConfiguration
    static class OpsQueryConfiguration {

        @Bean
        OpsOverviewQryExe opsOverviewQryExe(OpsDatabasePort databasePort, OpsRedisPort redisPort,
                                            OpsMeiliSearchPort meiliSearchPort) {
            return new OpsOverviewQryExe(databasePort, redisPort, meiliSearchPort);
        }
    }
}
