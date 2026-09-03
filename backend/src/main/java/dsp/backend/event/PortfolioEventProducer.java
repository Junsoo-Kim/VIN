package dsp.backend.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PortfolioEventProducer {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioEventProducer.class);
    public static final String TOPIC = "portfolio-events";

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * DB 커밋이 끝난 뒤 호출한다(PortfolioLockFacade 참고).
     * Kafka 발행 자체는 fire-and-forget: 브로커 장애가 사용자 요청(매수/매도)을 실패시키지 않도록
     * 예외를 흡수하고 로그만 남긴다. 발행이 유실되더라도 원본 데이터는 이미 DB에 안전하게 커밋된 상태다.
     */
    public void publish(PortfolioChangedEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.getUserId(), event);
        } catch (Exception e) {
            logger.error("포트폴리오 이벤트 발행 실패: userId={}, etfCode={}", event.getUserId(), event.getEtfCode(), e);
        }
    }
}
