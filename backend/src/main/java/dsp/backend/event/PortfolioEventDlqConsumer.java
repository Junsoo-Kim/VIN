package dsp.backend.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dsp.backend.Entity.PortfolioSyncFailure;
import dsp.backend.repository.PortfolioSyncFailureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class PortfolioEventDlqConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioEventDlqConsumer.class);

    @Autowired
    private PortfolioSyncFailureRepository portfolioSyncFailureRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(
            topics = PortfolioEventProducer.TOPIC + ".DLT",
            groupId = "${spring.kafka.consumer.group-id}-dlt")
    public void onFailedPortfolioEvent(
            PortfolioChangedEvent event,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage) {

        logger.error("포트폴리오 이벤트 최종 실패 → 보상 레코드 기록: userId={}, etfCode={}, reason={}",
                event.getUserId(), event.getEtfCode(), exceptionMessage);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            payload = String.valueOf(event);
        }

        portfolioSyncFailureRepository.save(
                new PortfolioSyncFailure(event.getUserId(), event.getEtfCode(), payload, exceptionMessage));
    }
}
