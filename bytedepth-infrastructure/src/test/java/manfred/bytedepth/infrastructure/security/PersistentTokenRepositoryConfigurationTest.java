package manfred.bytedepth.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class PersistentTokenRepositoryConfigurationTest {

    @Test
    void configuresJdbcBackedRememberMeTokens() {
        var repository = new PersistentTokenRepositoryConfiguration().persistentTokenRepository(mock(DataSource.class));

        assertInstanceOf(JdbcTokenRepositoryImpl.class, repository);
    }
}
