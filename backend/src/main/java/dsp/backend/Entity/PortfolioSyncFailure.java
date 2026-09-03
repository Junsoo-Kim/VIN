package dsp.backend.Entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 보상 트랜잭션(Compensating Transaction) 레코드.
 *
 * 포트폴리오 매수/매도 자체는 이미 DB에 커밋되어 성공한 상태이므로, 그 이후 단계인
 * "이력 적재(Consumer)"가 재시도까지 모두 실패했다고 해서 이미 끝난 매수/매도를 되돌릴 수는 없다.
 * 대신 실패 사실을 이 테이블에 남겨 재처리/수동 정합성 점검(reconciliation)의 대상으로 삼는다.
 * 즉 "원래 트랜잭션을 취소"하는 보상이 아니라 "실패를 명시적으로 기록해 후속 조치를 보장"하는
 * 보상 트랜잭션이다.
 */
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
