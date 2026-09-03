package dsp.backend.event;

import dsp.backend.Entity.PortfolioHistory;
import dsp.backend.repository.PortfolioHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 포트폴리오 변경 이벤트를 받아 이력을 적재한다.
 * 여기서 예외가 발생하면 KafkaConfig의 DefaultErrorHandler가 지수 백오프로 재시도하고,
 * 그래도 실패하면 "portfolio-events.DLT"로 보낸다 (PortfolioEventDlqConsumer 참고).
 */
@Component
public class PortfolioEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioEventConsumer.class);

    @Autowired
    private PortfolioHistoryRepository portfolioHistoryRepository;

    @KafkaListener(topics = PortfolioEventProducer.TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onPortfolioChanged(PortfolioChangedEvent event) {
        logger.info("포트폴리오 이력 적재: userId={}, etfCode={}, changeType={}",
                event.getUserId(), event.getEtfCode(), event.getChangeType());

        PortfolioHistory history = new PortfolioHistory(
                event.getUserId(),
                event.getEtfCode(),
                event.getChangeType(),
                event.getCount(),
                event.getOccurredAt());

        portfolioHistoryRepository.save(history);
    }
}
