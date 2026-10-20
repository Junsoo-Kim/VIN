package dsp.backend.service;

import dsp.backend.Entity.User;
import dsp.backend.exception.AlreadyLoggedInException;
import dsp.backend.exception.InvalidPasswordException;
import dsp.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = new UserService();
        ReflectionTestUtils.setField(userService, "userRepository", userRepository);
        ReflectionTestUtils.setField(userService, "passwordEncoder", passwordEncoder);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registerUser_storesHashedPassword() {
        User saved = userService.registerUser(user("alice", "secret"));

        assertThat(saved.getPassword()).isNotEqualTo("secret");
        assertThat(passwordEncoder.matches("secret", saved.getPassword())).isTrue();
    }

    @Test
    void registerUser_rejectsInvalidEmail() {
        User user = user("alice", "secret");
        user.setEmail("not-an-email");

        assertThatThrownBy(() -> userService.registerUser(user)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginUser_acceptsHashedPassword() {
        User stored = user("alice", passwordEncoder.encode("secret"));
        when(userRepository.findByUserId("alice")).thenReturn(Optional.of(stored));

        assertThat(userService.loginUser("alice", "secret", "")).isSameAs(stored);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginUser_migratesLegacyPlaintextPassword() {
        User stored = user("alice", "secret");
        when(userRepository.findByUserId("alice")).thenReturn(Optional.of(stored));

        userService.loginUser("alice", "secret", "");

        assertThat(passwordEncoder.matches("secret", stored.getPassword())).isTrue();
        verify(userRepository).save(stored);
    }

    @Test
    void loginUser_rejectsWrongPassword() {
        when(userRepository.findByUserId("alice")).thenReturn(Optional.of(user("alice", passwordEncoder.encode("secret"))));

        assertThatThrownBy(() -> userService.loginUser("alice", "wrong", "")).isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void loginUser_rejectsWhenAlreadyLoggedIn() {
        assertThatThrownBy(() -> userService.loginUser("alice", "secret", "alice"))
                .isInstanceOf(AlreadyLoggedInException.class);
    }

    private User user(String userId, String password) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword(password);
        user.setEmail(userId + "@example.com");
        return user;
    }
}
