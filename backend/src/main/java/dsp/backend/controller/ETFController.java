package dsp.backend.controller;

import dsp.backend.service.ETFService;
import dsp.backend.Entity.ETF;
import dsp.backend.Entity.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/etf")
public class ETFController {

    private static final Logger logger = LoggerFactory.getLogger(ETFService.class);

    @Autowired
    private ETFService etfService;

    @GetMapping("/{symbol}")
    public ResponseEntity<Map<String, Object>> getETFInfo(@PathVariable String symbol) {
        Optional<Map<String, Object>> etfInfo = etfService.getETFInfo(symbol);
        if (etfInfo.isPresent()) {
            return ResponseEntity.ok(etfInfo.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/price/{symbol}")
    public ResponseEntity<List<Map<String, String>>> getAllPrice(@PathVariable String symbol) {
        try {
            List<Map<String, String>> priceData = etfService.getAllPrice(symbol);
            if (priceData.isEmpty()) {
                return ResponseEntity.status(404).body(null);
            }
            return ResponseEntity.ok(priceData);
        } catch (Exception e) {
            logger.error("Error in getting price data for ETF: " + e);
            return ResponseEntity.status(404).body(null);
        }
    }

    @PostMapping("/simple")
    public ResponseEntity<List<Map<String, Object>>> getLowestPriceETFs(@RequestBody List<String> symbols) {
        try {
            List<Map<String, Object>> etfDetails = etfService.getsimpleETFPrices(symbols);
            return ResponseEntity.ok(etfDetails);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    @PostMapping("/details")
    public ResponseEntity<List<Map<String, Object>>> getETFDetails(@RequestBody List<String> symbols) {
        try {
            List<Map<String, Object>> etfDetails = etfService.getETFDetails(symbols);
            return ResponseEntity.ok(etfDetails);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllETFs() {
        try {
            List<Map<String, Object>>  symbols = etfService.getAllETFs();
            return ResponseEntity.ok(symbols);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    @GetMapping("/recommend/{symbol}/{userId}")

    public ResponseEntity<Map<String, Object>> recommendETF(@PathVariable String symbol, @PathVariable String userId) {
        return ResponseEntity.ok(etfService.recommendETF(symbol, userId));
    }
}
