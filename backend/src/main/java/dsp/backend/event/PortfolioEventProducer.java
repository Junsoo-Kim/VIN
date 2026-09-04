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

    public void publish(PortfolioChangedEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.getUserId(), event);
        } catch (Exception e) {
            logger.error("포트폴리오 이벤트 발행 실패: userId={}, etfCode={}", event.getUserId(), event.getEtfCode(), e);
        }
    }
}
