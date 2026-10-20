package dsp.backend.auth;

import dsp.backend.exception.UnauthorizedException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final SessionService sessionService;
    private final boolean enabled;

    public AuthInterceptor(SessionService sessionService, @Value("${vin.auth.enabled:true}") boolean enabled) {
        this.sessionService = sessionService;
        this.enabled = enabled;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (!enabled || CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        String userId = sessionService.resolve(readToken(request))
                .orElseThrow(() -> new UnauthorizedException("로그인이 필요합니다."));
        request.setAttribute(AuthContext.USER_ID_ATTRIBUTE, userId);
        return true;
    }

    public static String readToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> SessionService.COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
