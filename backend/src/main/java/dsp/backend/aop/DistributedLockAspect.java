package dsp.backend.aop;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class DistributedLockAspect {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockAspect.class);
    private static final String LOCK_PREFIX = "lock:";

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private MeterRegistry meterRegistry;

    @Around("@annotation(distributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        String key = LOCK_PREFIX + CustomSpringELParser.getDynamicValue(
                signature.getParameterNames(), joinPoint.getArgs(), distributedLock.key());

        RLock rLock = redissonClient.getLock(key);
        boolean acquired = false;
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            acquired = rLock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.timeUnit());
            sample.stop(Timer.builder("distributed_lock_wait")
                    .tag("result", acquired ? "acquired" : "rejected")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meterRegistry));
            if (!acquired) {
                logger.warn("분산 락 획득 실패: key={}", key);
                meterRegistry.counter("distributed_lock_rejected").increment();
                throw new LockAcquisitionException("다른 요청이 처리 중입니다. 잠시 후 다시 시도해주세요.");
            }
            return joinPoint.proceed();
        } finally {
            if (acquired && rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }
    }
}
