package manfred.bytedepth.infrastructure.user;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyBatisUserAuthenticationAdapterTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final MyBatisUserAuthenticationAdapter adapter = new MyBatisUserAuthenticationAdapter(userMapper);

    @Test
    void returnsAuthenticationDataAndPermissionsForExistingUser() {
        UserDO user = new UserDO();
        user.setId(7L);
        user.setUsername("alice");
        user.setPassword("hash");
        user.setStatus("ACTIVE");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userMapper.selectPermissionCodesByUserId(7L)).thenReturn(List.of("blog:post:create"));

        var result = adapter.findByUsername("alice");

        assertTrue(result.isPresent());
        var authentication = result.orElseThrow();
        assertEquals(7L, authentication.id());
        assertEquals("alice", authentication.username());
        assertEquals("hash", authentication.passwordHash());
        assertEquals("ACTIVE", authentication.status());
        assertEquals(List.of("blog:post:create"), authentication.permissionCodes());
        verify(userMapper).selectPermissionCodesByUserId(7L);
    }

    @Test
    void returnsEmptyWithoutLoadingPermissionsWhenUserIsMissing() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertFalse(adapter.findByUsername("missing").isPresent());

        verify(userMapper, never()).selectPermissionCodesByUserId(any());
    }
}
