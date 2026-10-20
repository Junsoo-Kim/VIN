package dsp.backend.controller;

import dsp.backend.Entity.User;
import dsp.backend.auth.AuthInterceptor;
import dsp.backend.auth.SessionService;
import dsp.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @Autowired
    private SessionService sessionService;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody User user) {
        userService.registerUser(user);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/signin")
    public ResponseEntity<?> signin(@RequestBody Map<String, String> loginRequest) {
        String userId = loginRequest.get("userId");
        String password = loginRequest.get("password");
        String currentCookie = loginRequest.get("currentCookie");
        User user = userService.loginUser(userId, password, currentCookie);
        String token = sessionService.create(user.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, sessionCookie(token, sessionService.getTtl().getSeconds()).toString())
                .body(Collections.singletonMap("userId", user.getUserId()));
    }

    @PostMapping("/signout")
    public ResponseEntity<?> signout(@RequestBody Map<String, String> logoutRequest, HttpServletRequest request) {
        String currentCookie = logoutRequest.get("currentCookie");
        userService.logoutUser(currentCookie);
        sessionService.invalidate(AuthInterceptor.readToken(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookie("", 0).toString())
                .build();
    }

    private ResponseCookie sessionCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(SessionService.COOKIE_NAME, value)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }
}