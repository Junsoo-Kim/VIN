package dsp.backend.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PriceConverterTest {

    private final PriceConverter converter = new PriceConverter(1000);

    @Test
    void convertsOnlyUsdPrices() {
        assertThat(converter.toKrw(CountryEnum.USA, 1.5)).isEqualTo(1500.0);
        assertThat(converter.toKrw(CountryEnum.KOREA, 1.5)).isEqualTo(1.5);
    }
}
