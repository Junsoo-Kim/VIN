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

/**
 * 재시도(지수 백오프)까지 모두 실패한 포트폴리오 이벤트를 받는 DLT(Dead Letter Topic) 컨슈머.
 *
 * 이미 커밋된 매수/매도 자체를 되돌리는 대신, "재처리가 필요하다"는 사실을
 * portfolio_sync_failures 테이블에 명시적으로 남긴다 — 이것이 이 시스템의 보상 트랜잭션이다.
 */
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
