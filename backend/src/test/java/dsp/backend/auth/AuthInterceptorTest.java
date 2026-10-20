package dsp.backend.auth;

import dsp.backend.exception.ForbiddenException;
import dsp.backend.exception.UnauthorizedException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {

    private final SessionService sessionService = mock(SessionService.class);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void rejectsRequestWithoutSession() {
        when(sessionService.resolve(any())).thenReturn(Optional.empty());
        AuthInterceptor interceptor = new AuthInterceptor(sessionService, true);

        assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest("GET", "/portfolio/alice"), response, new Object()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void storesAuthenticatedUserAndChecksOwnership() {
        when(sessionService.resolve("token")).thenReturn(Optional.of("alice"));
        AuthInterceptor interceptor = new AuthInterceptor(sessionService, true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/portfolio/alice");
        request.setCookies(new Cookie(SessionService.COOKIE_NAME, "token"));

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(request.getAttribute(AuthContext.USER_ID_ATTRIBUTE)).isEqualTo("alice");
        assertThatCode(() -> AuthContext.requireUser(request, "alice")).doesNotThrowAnyException();
        assertThatThrownBy(() -> AuthContext.requireUser(request, "bob")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void allowsCorsPreflightWithoutSession() {
        AuthInterceptor interceptor = new AuthInterceptor(sessionService, true);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/portfolio");
        request.addHeader("Origin", "http://localhost:3000");
        request.addHeader("Access-Control-Request-Method", "POST");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void skipsAuthenticationWhenDisabled() {
        AuthInterceptor interceptor = new AuthInterceptor(sessionService, false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/portfolio/alice");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThatCode(() -> AuthContext.requireUser(request, "bob")).doesNotThrowAnyException();
    }
}
