package dsp.backend.service;

import dsp.backend.Entity.Portfolio;
import dsp.backend.aop.DistributedLock;
import dsp.backend.event.PortfolioChangedEvent;
import dsp.backend.event.PortfolioEventProducer;
import dsp.backend.utils.PortfolioChangeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PortfolioLockFacade {

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private PortfolioEventProducer portfolioEventProducer;

    @DistributedLock(key = "'portfolio:' + #userId + ':' + #etfCode")
    public void addPortfolio(String userId, String etfCode, Integer count) {
        Portfolio saved = portfolioService.addPortfolio(userId, etfCode, count);

        portfolioEventProducer.publish(new PortfolioChangedEvent(
                saved.getUserId(), saved.getSymbol(), PortfolioChangeType.BUY, count, Instant.now()));
    }

    @DistributedLock(key = "'portfolio:' + #userId + ':' + #etfCode")
    public void deletePortfolio(String userId, String etfCode) {
        portfolioService.deletePortfolio(userId, etfCode);

        portfolioEventProducer.publish(new PortfolioChangedEvent(
                userId, etfCode, PortfolioChangeType.SELL, null, Instant.now()));
    }
}
