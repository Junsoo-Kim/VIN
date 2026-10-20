package dsp.backend.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PriceConverter {

    private final double usdKrw;

    public PriceConverter(@Value("${vin.exchange-rate.usd-krw:1395}") double usdKrw) {
        this.usdKrw = usdKrw;
    }

    public double toKrw(CountryEnum country, double price) {
        return country == CountryEnum.USA ? price * usdKrw : price;
    }
}
