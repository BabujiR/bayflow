package com.bayflow.appointment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VinDecoderTest {

    @Test
    void checkDigitCatchesATypo() {
        assertThat(VinDecoder.checkDigitMatches("1HGCM82633A004352")).isTrue();
        // One character off (6 → 4 in the check digit's place) no longer adds up.
        assertThat(VinDecoder.checkDigitMatches("1HGCM82643A004352")).isFalse();
        assertThat(VinDecoder.checkDigitMatches("11111111111111111")).isTrue();
    }

    @Test
    void makesWrittenInCapitalsAreTitleCased() {
        assertThat(VinDecoder.name("HONDA")).isEqualTo("Honda");
        assertThat(VinDecoder.name("MERCEDES-BENZ")).isEqualTo("Mercedes-Benz");
        assertThat(VinDecoder.name("LAND ROVER")).isEqualTo("Land Rover");
        assertThat(VinDecoder.name("BMW")).isEqualTo("BMW");
        assertThat(VinDecoder.name("Tesla")).isEqualTo("Tesla");
        assertThat(VinDecoder.name("  ")).isNull();
    }

    @Test
    void engineIsDescribedFromWhatIsKnown() {
        assertThat(VinDecoder.engine(result("1.996", "4", "Gasoline", null))).isEqualTo("2.0L 4-cyl Gasoline");
        assertThat(VinDecoder.engine(result(null, null, "Electric", "BEV (Battery Electric Vehicle)")))
                .isEqualTo("Electric");
        assertThat(VinDecoder.engine(result("2.5", "4", "Gasoline", "Strong HEV (Hybrid Electric Vehicle)")))
                .isEqualTo("2.5L 4-cyl Hybrid");
        assertThat(VinDecoder.engine(result("Not Applicable", "", null, null))).isNull();
    }

    private static VinDecoder.Result result(String displacement, String cylinders, String fuel,
            String electrification) {
        return new VinDecoder.Result("2019", "HONDA", "Civic", "EX", null, displacement, cylinders, fuel,
                electrification, "Sedan/Saloon");
    }
}
