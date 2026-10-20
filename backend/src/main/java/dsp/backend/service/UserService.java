package dsp.backend.service;

import dsp.backend.Entity.User;
import dsp.backend.exception.UserAlreadyExistsException;
import dsp.backend.exception.UserNotFoundException;
import dsp.backend.exception.InvalidPasswordException;
import dsp.backend.exception.AlreadyLoggedInException;
import dsp.backend.exception.UnauthorizedException;
import dsp.backend.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
    );

    public User registerUser(User user) {
        if (!EMAIL_PATTERN.matcher(user.getEmail()).matches()) {
            throw new RuntimeException("Invalid email format");
        }
        if (userRepository.existsByUserId(user.getUserId())) {
            throw new UserAlreadyExistsException("UserId already exists");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new UserAlreadyExistsException("Email already exists");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public User loginUser(String userId, String password, String currentCookie) {
        if (currentCookie != "") {
            throw new AlreadyLoggedInException("로그인 상태에서 다시 로그인할 수 없습니다.");
        }
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException("존재하지 않는 아이디입니다."));
        if (!matchesPassword(user, password)) {
            throw new InvalidPasswordException("올바르지 않은 비밀번호입니다.");
        }
        return user;
    }

    private boolean matchesPassword(User user, String rawPassword) {
        String stored = user.getPassword();
        if (rawPassword == null || stored == null) {
            return false;
        }
        if (isBcryptHash(stored)) {
            return passwordEncoder.matches(rawPassword, stored);
        }
        if (stored.equals(rawPassword)) {
            user.setPassword(passwordEncoder.encode(rawPassword));
            userRepository.save(user);
            return true;
        }
        return false;
    }

    private boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    public void logoutUser(String currentCookie) {
        if (currentCookie == null) {
            throw new UnauthorizedException("비로그인 상태에서 로그아웃할 수 없습니다.");
        }
    }
}