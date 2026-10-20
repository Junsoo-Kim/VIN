package dsp.backend.auth;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Service
public class SessionService {

    public static final String COOKIE_NAME = "VIN_SESSION";
    private static final String KEY_PREFIX = "session:";

    private final RedissonClient redissonClient;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public SessionService(RedissonClient redissonClient,
                          @Value("${vin.auth.session-ttl-minutes:120}") long ttlMinutes) {
        this.redissonClient = redissonClient;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public String create(String userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        bucket(token).set(userId, ttl);
        return token;
    }

    public Optional<String> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(bucket(token).get());
    }

    public void invalidate(String token) {
        if (token != null && !token.isBlank()) {
            bucket(token).delete();
        }
    }

    public Duration getTtl() {
        return ttl;
    }

    private RBucket<String> bucket(String token) {
        return redissonClient.getBucket(KEY_PREFIX + token);
    }
}
