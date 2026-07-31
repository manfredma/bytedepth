package manfred.bytedepth.app.user;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoadUserAuthenticationQryExeTest {

    private final UserAuthenticationPort port = mock(UserAuthenticationPort.class);
    private final LoadUserAuthenticationQryExe exe = new LoadUserAuthenticationQryExe(port);

    @Test
    void returnsAuthenticationDataFromPort() {
        UserAuthentication user = new UserAuthentication(1L, "alice", "hash", "ACTIVE", List.of("blog:post:create"));
        when(port.findByUsername("alice")).thenReturn(Optional.of(user));

        var result = exe.execute("alice");

        assertTrue(result.isPresent());
        assertEquals(user, result.orElseThrow());
        assertEquals(List.of("blog:post:create"), result.orElseThrow().permissionCodes());
    }

    @Test
    void returnsEmptyWhenPortCannotFindUser() {
        when(port.findByUsername("missing")).thenReturn(Optional.empty());

        assertFalse(exe.execute("missing").isPresent());
    }
}
