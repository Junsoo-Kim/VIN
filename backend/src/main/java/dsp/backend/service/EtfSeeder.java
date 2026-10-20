package dsp.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class EtfSeeder {

    @Autowired
    private ETFService etfService;

    @Value("${vin.seed.enabled:false}")
    private boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!enabled) {
            return;
        }
        etfService.saveYahooETFs();
        etfService.saveNaverETFs();
    }
}
