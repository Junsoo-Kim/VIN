package dsp.backend.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dsp.backend.Entity.PortfolioSyncFailure;
import dsp.backend.repository.PortfolioSyncFailureRepository;
import dsp.backend.utils.PortfolioChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioEventProducerTest {

    private KafkaTemplate<String, Object> kafkaTemplate;
    private PortfolioSyncFailureRepository failureRepository;
    private PortfolioEventProducer producer;
    private final PortfolioChangedEvent event =
            new PortfolioChangedEvent("alice", "069500", PortfolioChangeType.BUY, 1, Instant.now());

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        failureRepository = mock(PortfolioSyncFailureRepository.class);
        producer = new PortfolioEventProducer();
        ReflectionTestUtils.setField(producer, "kafkaTemplate", kafkaTemplate);
        ReflectionTestUtils.setField(producer, "portfolioSyncFailureRepository", failureRepository);
        ReflectionTestUtils.setField(producer, "objectMapper", new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void recordsFailureWhenAsyncSendFails() {
        when(kafkaTemplate.send(eq(PortfolioEventProducer.TOPIC), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        producer.publish(event);

        ArgumentCaptor<PortfolioSyncFailure> failure = ArgumentCaptor.forClass(PortfolioSyncFailure.class);
        verify(failureRepository).save(failure.capture());
        assertThat(failure.getValue().getUserId()).isEqualTo("alice");
        assertThat(failure.getValue().getFailureReason()).contains("broker down");
    }

    @Test
    void recordsFailureWhenSendThrows() {
        when(kafkaTemplate.send(eq(PortfolioEventProducer.TOPIC), anyString(), any()))
                .thenThrow(new RuntimeException("serialization"));

        producer.publish(event);

        verify(failureRepository).save(any(PortfolioSyncFailure.class));
    }

    @Test
    void doesNotRecordFailureOnSuccess() {
        when(kafkaTemplate.send(eq(PortfolioEventProducer.TOPIC), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publish(event);

        verify(failureRepository, never()).save(any());
    }
}
