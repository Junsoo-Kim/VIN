package dsp.backend.auth;

import dsp.backend.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;

public final class AuthContext {

    public static final String USER_ID_ATTRIBUTE = "authUserId";

    private AuthContext() {
    }

    public static void requireUser(HttpServletRequest request, String userId) {
        Object authUserId = request.getAttribute(USER_ID_ATTRIBUTE);
        if (authUserId != null && !authUserId.equals(userId)) {
            throw new ForbiddenException("다른 사용자의 정보에 접근할 수 없습니다.");
        }
    }
}
