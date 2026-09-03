package dsp.backend.repository;

import dsp.backend.Entity.PortfolioHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioHistoryRepository extends JpaRepository<PortfolioHistory, Long> {
}
