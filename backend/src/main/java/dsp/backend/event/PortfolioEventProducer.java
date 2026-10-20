package dsp.backend.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dsp.backend.Entity.PortfolioSyncFailure;
import dsp.backend.repository.PortfolioSyncFailureRepository;
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

    @Autowired
    private PortfolioSyncFailureRepository portfolioSyncFailureRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public void publish(PortfolioChangedEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.getUserId(), event).whenComplete((result, ex) -> {
                if (ex != null) {
                    recordFailure(event, ex);
                }
            });
        } catch (Exception e) {
            recordFailure(event, e);
        }
    }

    private void recordFailure(PortfolioChangedEvent event, Throwable cause) {
        logger.error("포트폴리오 이벤트 발행 실패 → 보상 레코드 기록: userId={}, etfCode={}",
                event.getUserId(), event.getEtfCode(), cause);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            payload = String.valueOf(event);
        }

        try {
            portfolioSyncFailureRepository.save(
                    new PortfolioSyncFailure(event.getUserId(), event.getEtfCode(), payload, "PUBLISH_FAILED: " + cause.getMessage()));
        } catch (Exception e) {
            logger.error("보상 레코드 기록 실패: payload={}", payload, e);
        }
    }
}
