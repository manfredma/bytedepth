package manfred.bytedepth.adapter.web.security;

import manfred.bytedepth.app.user.LoadUserAuthenticationQryExe;
import manfred.bytedepth.app.user.UserAuthentication;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiteUserDetailsServiceTest {

    private final LoadUserAuthenticationQryExe authenticationQuery = mock(LoadUserAuthenticationQryExe.class);
    private final SiteUserDetailsService service = new SiteUserDetailsService(authenticationQuery);

    @Test
    void mapsAnActiveUserToSpringSecurityPrincipal() {
        when(authenticationQuery.execute("alice")).thenReturn(Optional.of(user("ACTIVE")));

        SiteUserDetails details = (SiteUserDetails) service.loadUserByUsername("alice");

        assertThat(details.getId()).isEqualTo(1L);
        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getPassword()).isEqualTo("hash");
        assertThat(details.getAuthorities()).extracting(authority -> authority.getAuthority())
                .containsExactly("blog:post:create");
        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void rejectsMissingPendingAndBannedUsers() {
        when(authenticationQuery.execute("missing")).thenReturn(Optional.empty());
        when(authenticationQuery.execute("pending")).thenReturn(Optional.of(user("PENDING")));
        when(authenticationQuery.execute("banned")).thenReturn(Optional.of(user("BANNED")));

        assertThatThrownBy(() -> service.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class);
        assertThatThrownBy(() -> service.loadUserByUsername("pending"))
                .isInstanceOf(DisabledException.class);
        assertThatThrownBy(() -> service.loadUserByUsername("banned"))
                .isInstanceOf(LockedException.class);
    }

    private UserAuthentication user(String status) {
        return new UserAuthentication(1L, "alice", "hash", status, List.of("blog:post:create"));
    }
}
