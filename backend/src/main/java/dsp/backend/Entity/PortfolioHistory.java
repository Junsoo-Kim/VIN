package dsp.backend.Entity;

import dsp.backend.utils.PortfolioChangeType;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * 포트폴리오 매수/매도 이력. PortfolioService의 트랜잭션과는 별개로,
 * Kafka Consumer가 비동기로 적재한다(critical path와 분리).
 */
@Entity
@Table(name = "portfolio_history")
public class PortfolioHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String etfCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PortfolioChangeType changeType;

    @Column(nullable = false)
    private Integer count;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private Instant recordedAt;

    public PortfolioHistory() {
    }

    public PortfolioHistory(String userId, String etfCode, PortfolioChangeType changeType, Integer count, Instant occurredAt) {
        this.userId = userId;
        this.etfCode = etfCode;
        this.changeType = changeType;
        this.count = count;
        this.occurredAt = occurredAt;
        this.recordedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getEtfCode() {
        return etfCode;
    }

    public PortfolioChangeType getChangeType() {
        return changeType;
    }

    public Integer getCount() {
        return count;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
