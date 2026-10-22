package manfred.bytedepth.app.user;

import manfred.bytedepth.domain.common.DomainException;

/** Stable account conflict independent of localized validation messages. */
public final class UsernameTakenException extends DomainException {
    public UsernameTakenException() {
        super("用户名已存在");
    }
}
