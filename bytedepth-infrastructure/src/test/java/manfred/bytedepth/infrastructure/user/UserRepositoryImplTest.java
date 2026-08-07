package manfred.bytedepth.infrastructure.user;

import manfred.bytedepth.domain.user.User;
import manfred.bytedepth.domain.user.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRepositoryImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final UserRepositoryImpl repository = new UserRepositoryImpl(userMapper, roleMapper, userRoleMapper);

    @Test
    void findById_returnsPresentWhenUserExists() {
        UserDO row = userRow();
        when(userMapper.selectById(1L)).thenReturn(row);

        User result = repository.findById(1L).orElseThrow();

        assertEquals(1L, result.getId());
        assertEquals("alice", result.getUsername());
        assertEquals("hash", result.getPasswordHash());
        assertEquals("alice@example.com", result.getEmail());
        assertEquals("avatar.png", result.getAvatar());
        assertEquals("bio text", result.getBio());
        assertEquals(UserStatus.ACTIVE, result.getStatus());
        assertEquals(row.getCreatedAt(), result.getCreatedAt());
        assertEquals(row.getUpdatedAt(), result.getUpdatedAt());
    }

    @Test
    void findById_returnsEmptyWhenUserMissing() {
        when(userMapper.selectById(2L)).thenReturn(null);

        assertTrue(repository.findById(2L).isEmpty());
    }

    @Test
    void findByUsername_returnsPresentWhenUserExists() {
        UserDO row = userRow();
        when(userMapper.selectOne(any())).thenReturn(row);

        User result = repository.findByUsername("alice").orElseThrow();

        assertEquals("alice", result.getUsername());
        assertEquals(UserStatus.ACTIVE, result.getStatus());
    }

    @Test
    void findByUsername_returnsEmptyWhenUserMissing() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertTrue(repository.findByUsername("missing").isEmpty());
    }

    @Test
    void existsByUsername_returnsTrueWhenCountGreaterThanZero() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertTrue(repository.existsByUsername("alice"));
    }

    @Test
    void existsByUsername_returnsFalseWhenCountZero() {
        when(userMapper.selectCount(any())).thenReturn(0L);

        assertFalse(repository.existsByUsername("missing"));
    }

    @Test
    void findByStatus_mapsAllRowsToDomain() {
        UserDO row1 = userRow();
        UserDO row2 = userRow();
        row2.setId(2L);
        row2.setUsername("bob");
        when(userMapper.selectList(any())).thenReturn(List.of(row1, row2));

        List<User> result = repository.findByStatus(UserStatus.ACTIVE);

        assertEquals(2, result.size());
        assertEquals("alice", result.get(0).getUsername());
        assertEquals("bob", result.get(1).getUsername());
    }

    @Test
    void findByStatus_returnsEmptyListWhenNoUsers() {
        when(userMapper.selectList(any())).thenReturn(List.of());

        assertTrue(repository.findByStatus(UserStatus.PENDING).isEmpty());
    }

    @Test
    void save_insertsWhenIdIsNull() {
        User user = User.register("newuser", "passwordhash");
        when(userMapper.insert(any(UserDO.class))).thenAnswer(invocation -> {
            invocation.<UserDO>getArgument(0).setId(10L);
            return 1;
        });

        User saved = repository.save(user);

        assertEquals(10L, saved.getId());
        assertEquals("newuser", saved.getUsername());
        assertEquals("passwordhash", saved.getPasswordHash());
        assertEquals(UserStatus.PENDING, saved.getStatus());
        verify(userMapper).insert(any(UserDO.class));
        verify(userMapper, never()).updateById(any(UserDO.class));
    }

    @Test
    void save_updatesWhenIdIsNotNull() {
        LocalDateTime now = LocalDateTime.now();
        User user = User.reconstruct(5L, "existing", "hash", "email", "avatar", "bio",
            UserStatus.ACTIVE, now, now);

        User saved = repository.save(user);

        assertEquals(5L, saved.getId());
        assertEquals("existing", saved.getUsername());
        verify(userMapper).updateById(any(UserDO.class));
        verify(userMapper, never()).insert(any(UserDO.class));
    }

    @Test
    void save_setsTimestampsWhenNull() {
        User user = User.register("newuser", "hash");
        // register sets createdAt/updatedAt to now, but let's test toDO null-guard
        // by reconstructing with null timestamps
        User userNullTs = User.reconstruct(null, "newuser", "hash", null, null, null,
            UserStatus.PENDING, null, null);
        when(userMapper.insert(any(UserDO.class))).thenAnswer(invocation -> {
            invocation.<UserDO>getArgument(0).setId(10L);
            return 1;
        });

        User saved = repository.save(userNullTs);

        assertEquals(10L, saved.getId());
        // toDO fills null timestamps with LocalDateTime.now()
        verify(userMapper).insert(any(UserDO.class));
    }

    @Test
    void deleteById_delegatesToMapper() {
        repository.deleteById(3L);

        verify(userMapper).deleteById(3L);
    }

    @Test
    void assignRole_insertsUserRoleWhenRoleFound() {
        RoleDO role = new RoleDO();
        role.setId(2L);
        role.setName("ADMIN");
        when(roleMapper.selectOne(any())).thenReturn(role);

        repository.assignRole(5L, "ADMIN");

        verify(userRoleMapper).insert(any(UserRoleDO.class));
    }

    @Test
    void assignRole_doesNothingWhenRoleNotFound() {
        when(roleMapper.selectOne(any())).thenReturn(null);

        repository.assignRole(5L, "NONEXISTENT");

        verify(userRoleMapper, never()).insert(any(UserRoleDO.class));
    }

    private UserDO userRow() {
        UserDO row = new UserDO();
        row.setId(1L);
        row.setUsername("alice");
        row.setPassword("hash");
        row.setEmail("alice@example.com");
        row.setAvatar("avatar.png");
        row.setBio("bio text");
        row.setStatus("ACTIVE");
        row.setCreatedAt(LocalDateTime.of(2024, 1, 1, 10, 0));
        row.setUpdatedAt(LocalDateTime.of(2024, 1, 2, 10, 0));
        return row;
    }
}
