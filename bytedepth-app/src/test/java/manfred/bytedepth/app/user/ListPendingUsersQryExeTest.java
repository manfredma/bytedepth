package manfred.bytedepth.app.user;

import manfred.bytedepth.domain.user.User;
import manfred.bytedepth.domain.user.UserRepository;
import manfred.bytedepth.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListPendingUsersQryExeTest {

    @Mock private UserRepository userRepository;
    private ListPendingUsersQryExe exe;

    @BeforeEach
    void setUp() { exe = new ListPendingUsersQryExe(userRepository); }

    @Test
    void execute_returnsPendingUsers() {
        User pending = User.reconstruct(1L, "dave", "hash", null, null, null,
                UserStatus.PENDING, LocalDateTime.now(), LocalDateTime.now());
        when(userRepository.findByStatus(UserStatus.PENDING)).thenReturn(List.of(pending));

        List<UserDTO> result = exe.execute();

        assertEquals(1, result.size());
        assertEquals("dave", result.get(0).getUsername());
        assertEquals("PENDING", result.get(0).getStatus());
    }

    @Test
    void execute_noUsers_returnsEmptyList() {
        when(userRepository.findByStatus(UserStatus.PENDING)).thenReturn(List.of());
        assertTrue(exe.execute().isEmpty());
    }

    @Test
    void findPage_returnsMappedUsersAndFilteredTotal() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 11, 10, 0);
        User active = User.reconstruct(2L, "alice", "hash", null, null, null,
                UserStatus.ACTIVE, createdAt, createdAt);
        when(userRepository.findPage("ali", "ACTIVE", 2, 10)).thenReturn(List.of(active));
        when(userRepository.countFiltered("ali", "ACTIVE")).thenReturn(11L);

        ListPendingUsersQryExe.UserPageResult result = exe.findPage("ali", "ACTIVE", 2, 10);

        assertEquals(11L, result.total());
        assertEquals(1, result.users().size());
        assertEquals(2L, result.users().getFirst().getId());
        assertEquals("alice", result.users().getFirst().getUsername());
        assertEquals("ACTIVE", result.users().getFirst().getStatus());
        assertEquals(createdAt, result.users().getFirst().getCreatedAt());
        verify(userRepository).findPage("ali", "ACTIVE", 2, 10);
        verify(userRepository).countFiltered("ali", "ACTIVE");
    }
}
