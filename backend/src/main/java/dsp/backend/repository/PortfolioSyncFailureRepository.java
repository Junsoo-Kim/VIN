package dsp.backend.repository;

import dsp.backend.Entity.PortfolioSyncFailure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioSyncFailureRepository extends JpaRepository<PortfolioSyncFailure, Long> {
}
