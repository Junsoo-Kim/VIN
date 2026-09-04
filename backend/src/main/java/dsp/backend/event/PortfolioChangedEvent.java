package dsp.backend.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dsp.backend.utils.PortfolioChangeType;

import java.io.Serializable;
import java.time.Instant;

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
