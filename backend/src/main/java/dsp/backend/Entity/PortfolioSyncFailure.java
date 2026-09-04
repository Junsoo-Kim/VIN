package dsp.backend.Entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "portfolio_sync_failures")
public class PortfolioSyncFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String etfCode;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Column(nullable = false)
    private boolean resolved;

    @Column(nullable = false)
    private Instant failedAt;

    public PortfolioSyncFailure() {
    }

    public PortfolioSyncFailure(String userId, String etfCode, String payload, String failureReason) {
        this.userId = userId;
        this.etfCode = etfCode;
        this.payload = payload;
        this.failureReason = failureReason;
        this.resolved = false;
        this.failedAt = Instant.now();
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

    public String getPayload() {
        return payload;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public Instant getFailedAt() {
        return failedAt;
    }
}
