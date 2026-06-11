package manfred.bytedepth.app.user;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.user.UserRepository;
import manfred.bytedepth.domain.user.UserStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListPendingUsersQryExe {

    private final UserRepository userRepository;

    public List<UserDTO> execute() {
        return userRepository.findByStatus(UserStatus.PENDING).stream()
            .map(u -> {
                UserDTO dto = new UserDTO();
                dto.setId(u.getId());
                dto.setUsername(u.getUsername());
                dto.setStatus(u.getStatus().name());
                dto.setCreatedAt(u.getCreatedAt());
                return dto;
            })
            .collect(Collectors.toList());
    }
}
