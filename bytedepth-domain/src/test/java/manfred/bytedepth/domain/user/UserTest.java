package manfred.bytedepth.domain.user;

import manfred.bytedepth.domain.common.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void register_setsUsernameAndStatusPending() {
        User user = User.register("alice", "$2a$10$hash");
        assertEquals("alice", user.getUsername());
        assertEquals(UserStatus.PENDING, user.getStatus());
        assertNotNull(user.getCreatedAt());
    }

    @Test
    void activate_pendingUser_becomesActive() {
        User user = User.register("alice", "hash");
        user.activate();
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void activate_alreadyActive_throwsDomainException() {
        User user = User.register("alice", "hash");
        user.activate();
        assertThrows(DomainException.class, user::activate);
    }

    @Test
    void ban_activeUser_becomesBanned() {
        User user = User.register("alice", "hash");
        user.activate();
        user.ban();
        assertEquals(UserStatus.BANNED, user.getStatus());
    }

    @Test
    void ban_alreadyBanned_throwsDomainException() {
        User user = User.register("alice", "hash");
        user.activate();
        user.ban();
        assertThrows(DomainException.class, user::ban);
    }

    @Test
    void reconstruct_restoresEveryField() {
        var createdAt = java.time.LocalDateTime.of(2026, 8, 4, 10, 0);
        var updatedAt = createdAt.plusHours(1);
        User user = User.reconstruct(7L, "alice", "hash", "a@example.com", "avatar.png", "bio",
                UserStatus.ACTIVE, createdAt, updatedAt);

        assertEquals(7L, user.getId());
        assertEquals("hash", user.getPasswordHash());
        assertEquals("a@example.com", user.getEmail());
        assertEquals("avatar.png", user.getAvatar());
        assertEquals("bio", user.getBio());
        assertEquals(updatedAt, user.getUpdatedAt());
    }
}
