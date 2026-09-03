package dsp.backend.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dsp.backend.utils.PortfolioChangeType;

import java.io.Serializable;
import java.time.Instant;

/**
 * 포트폴리오 매수/매도가 DB에 커밋된 "이후" 발행되는 이벤트.
 * Kafka를 통해 이력 적재 등 부가 처리를 매수/매도 요청의 임계 경로(critical path)에서 분리한다.
 */
public class PortfolioChangedEvent implements Serializable {

    private String userId;
    private String etfCode;
    private PortfolioChangeType changeType;
    private Integer count;
    private Instant occurredAt;

    public PortfolioChangedEvent() {
    }

    @JsonCreator
    public PortfolioChangedEvent(
            @JsonProperty("userId") String userId,
            @JsonProperty("etfCode") String etfCode,
            @JsonProperty("changeType") PortfolioChangeType changeType,
            @JsonProperty("count") Integer count,
            @JsonProperty("occurredAt") Instant occurredAt) {
        this.userId = userId;
        this.etfCode = etfCode;
        this.changeType = changeType;
        this.count = count;
        this.occurredAt = occurredAt;
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
}
