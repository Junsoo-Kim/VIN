package dsp.backend.controller;

import dsp.backend.aop.LockAcquisitionException;
import dsp.backend.service.PortfolioLockFacade;
import dsp.backend.service.PortfolioService;
import dsp.backend.service.ResourceNotFoundException;
import dsp.backend.utils.CountryEnum;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/portfolio")
public class PortfolioController {

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private PortfolioLockFacade portfolioLockFacade;

    @PostMapping
    public ResponseEntity<String> addPortfolio(@RequestBody PortfolioRequest request) {
        try {
            portfolioLockFacade.addPortfolio(request.getUserId(), request.getEtfCode(), request.getCount());
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body("존재하지 않는 정보");
        } catch (LockAcquisitionException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @DeleteMapping
    public ResponseEntity<String> deletePortfolio(@RequestBody PortfolioRequest request) {
        try {
            portfolioLockFacade.deletePortfolio(request.getUserId(), request.getEtfCode());
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body("존재하지 않는 정보");
        } catch (LockAcquisitionException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<PortfolioResponse>> getPortfolios(@PathVariable String userId) {
        try {
            List<PortfolioResponse> portfolios = portfolioService.getPortfolios(userId);
            return ResponseEntity.ok(portfolios);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(null);
        }
    }

    public static class PortfolioRequest {
        private String userId;
        private String etfCode;
        private Integer count;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getEtfCode() {
            return etfCode;
        }

        public void setEtfCode(String etfCode) {
            this.etfCode = etfCode;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }
    }

    public static class PortfolioResponse {
        private String etfCode;
        private String etfName;
        private Integer count;
        private Double money;
        private Double percent;
        private CountryEnum country;

        // Getters and setters
        public String getEtfCode() {
            return etfCode;
        }

        public void setEtfCode(String etfCode) {
            this.etfCode = etfCode;
        }

        public String getEtfName() {
            return etfName;
        }

        public void setEtfName(String etfName) {
            this.etfName = etfName;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public Double getMoney() {
            return money;
        }

        public void setMoney(Double money) {
            this.money = money;
        }

        public Double getPercent() {
            return percent;
        }

        public void setPercent(Double percent) {
            this.percent = percent;
        }

        public CountryEnum getCountry() {
            return country;
        }

        public void setCountry(CountryEnum country) {
            this.country = country;
        }
    }
}
