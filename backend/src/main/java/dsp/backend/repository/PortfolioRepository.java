package dsp.backend.repository;

import dsp.backend.Entity.Portfolio;
import dsp.backend.Entity.PortfolioId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, PortfolioId> {

    @Query("SELECT p FROM Portfolio p JOIN FETCH p.etf WHERE p.userId = :userId")
    List<Portfolio> findByUserIdWithEtf(@Param("userId") String userId);
}
