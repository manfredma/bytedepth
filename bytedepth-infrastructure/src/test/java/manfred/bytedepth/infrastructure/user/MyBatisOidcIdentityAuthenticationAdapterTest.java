package manfred.bytedepth.infrastructure.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class MyBatisOidcIdentityAuthenticationAdapterTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final OidcIdentityMapper identityMapper = mock(OidcIdentityMapper.class);
    private final MyBatisOidcIdentityAuthenticationAdapter adapter =
            new MyBatisOidcIdentityAuthenticationAdapter(userMapper, identityMapper);

    @Test
    void authenticate_existingIdentityLoadsLocalPermissions() {
        OidcIdentityDO identity = identity(7L);
        UserDO user = user(7L, "alice", "ACTIVE");
        when(identityMapper.selectByIssuerAndSubject("https://hearth.example", "subject-1"))
                .thenReturn(identity);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(userMapper.selectPermissionCodesByUserId(7L)).thenReturn(List.of("blog:post:create"));

        var result = adapter.authenticate("https://hearth.example", "subject-1", "alice", "alice@example.com");

        assertTrue(result.isPresent());
        assertEquals(7L, result.orElseThrow().id());
        assertEquals(List.of("blog:post:create"), result.orElseThrow().permissionCodes());
    }

    @Test
    void authenticate_firstLoginCreatesPendingUserAndIdentity() {
        when(identityMapper.selectByIssuerAndSubject("https://hearth.example", "subject-1"))
                .thenReturn(null);
        when(userMapper.selectByUsername("alice")).thenReturn(null);
        when(userMapper.insert(any(UserDO.class))).thenAnswer(invocation -> {
            invocation.<UserDO>getArgument(0).setId(11L);
            return 1;
        });
        when(userMapper.selectPermissionCodesByUserId(11L)).thenReturn(List.of());

        var result = adapter.authenticate("https://hearth.example", "subject-1", "alice", "alice@example.com");

        assertEquals("PENDING", result.orElseThrow().status());
        verify(identityMapper).insert(any(OidcIdentityDO.class));
        assertEquals("alice", result.orElseThrow().username());
    }

    @Test
    void authenticate_existingIdentityWithoutLocalUserReturnsEmpty() {
        when(identityMapper.selectByIssuerAndSubject("https://hearth.example", "subject-1"))
                .thenReturn(identity(7L));
        when(userMapper.selectById(7L)).thenReturn(null);

        assertFalse(adapter.authenticate("https://hearth.example", "subject-1", "alice", null)
                .isPresent());
    }

    @Test
    void authenticate_firstLoginUsesStableSuffixWhenUsernameAlreadyExists() {
        when(identityMapper.selectByIssuerAndSubject("https://hearth.example", "subject-1"))
                .thenReturn(null);
        when(userMapper.selectByUsername("alice")).thenReturn(user(3L, "alice", "ACTIVE"));
        when(userMapper.insert(any(UserDO.class))).thenAnswer(invocation -> {
            invocation.<UserDO>getArgument(0).setId(11L);
            return 1;
        });
        when(userMapper.selectPermissionCodesByUserId(11L)).thenReturn(List.of());

        var result = adapter.authenticate("https://hearth.example", "subject-1", "alice", null);

        assertFalse(result.orElseThrow().username().equals("alice"));
    }

    @Test
    void authenticate_firstLoginFallsBackWhenPreferredUsernameIsBlank() {
        when(identityMapper.selectByIssuerAndSubject("https://hearth.example", "subject-1"))
                .thenReturn(null);
        when(userMapper.selectByUsername("hearth-user")).thenReturn(null);
        when(userMapper.insert(any(UserDO.class))).thenAnswer(invocation -> {
            invocation.<UserDO>getArgument(0).setId(11L);
            return 1;
        });
        when(userMapper.selectPermissionCodesByUserId(11L)).thenReturn(List.of());

        var result = adapter.authenticate("https://hearth.example", "subject-1", " ", null);

        assertEquals("hearth-user", result.orElseThrow().username());
    }

    @Test
    void authenticate_firstLoginFallsBackWhenPreferredUsernameIsNull() {
        when(identityMapper.selectByIssuerAndSubject("https://hearth.example", "subject-1"))
                .thenReturn(null);
        when(userMapper.selectByUsername("hearth-user")).thenReturn(null);
        when(userMapper.insert(any(UserDO.class))).thenAnswer(invocation -> {
            invocation.<UserDO>getArgument(0).setId(11L);
            return 1;
        });
        when(userMapper.selectPermissionCodesByUserId(11L)).thenReturn(List.of());

        var result = adapter.authenticate("https://hearth.example", "subject-1", null, null);

        assertEquals("hearth-user", result.orElseThrow().username());
    }

    private static OidcIdentityDO identity(long userId) {
        OidcIdentityDO value = new OidcIdentityDO();
        value.setUserId(userId);
        value.setIssuer("https://hearth.example");
        value.setSubject("subject-1");
        return value;
    }

    private static UserDO user(long id, String username, String status) {
        UserDO value = new UserDO();
        value.setId(id);
        value.setUsername(username);
        value.setPassword("");
        value.setStatus(status);
        return value;
    }
}
