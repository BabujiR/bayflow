package com.bayflow.appointment.integration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.bayflow.appointment.application.ApiException;
import com.bayflow.appointment.config.IntegrationProperties;
import com.bayflow.appointment.domain.Vehicle;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Decodes a VIN into year, make, model, trim and engine with NHTSA's vPIC service (free, no key):
 * staff type a VIN and the rest of the car fills in. Decoded cars are kept in memory (a VIN's
 * specs never change); a failure only means typing them by hand.
 */
@Component
public class VinDecoder {

    /** {@code warnings}: e.g. a check digit that doesn't match (a likely typo). */
    public record DecodedVin(String vin, Integer year, String make, String model, String trim, String engine,
            String bodyClass, List<String> warnings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(@JsonProperty("Results") List<Result> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(@JsonProperty("ModelYear") String modelYear, @JsonProperty("Make") String make,
            @JsonProperty("Model") String model, @JsonProperty("Trim") String trim,
            @JsonProperty("Series") String series, @JsonProperty("DisplacementL") String displacementL,
            @JsonProperty("EngineCylinders") String engineCylinders,
            @JsonProperty("FuelTypePrimary") String fuelTypePrimary,
            @JsonProperty("ElectrificationLevel") String electrificationLevel,
            @JsonProperty("BodyClass") String bodyClass) {
    }

    private static final Logger log = LoggerFactory.getLogger(VinDecoder.class);
    private static final int CACHE_SIZE = 5_000;
    private static final String WEIGHTS = "8765432X098765432";

    private final RestClient vpic;
    private final Map<String, DecodedVin> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, DecodedVin> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    public VinDecoder(IntegrationProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getVinDecoder().getTimeout());
        factory.setReadTimeout(properties.getVinDecoder().getTimeout());
        // An outside service: no request ids or trace headers go to it.
        this.vpic = RestClient.builder().baseUrl(properties.getVinDecoder().getUrl()).requestFactory(factory).build();
    }

    public DecodedVin decode(String vin) {
        String normalized = Vehicle.normalizeVin(vin);
        if (!Vehicle.VIN.matcher(normalized).matches()) {
            throw ApiException.badRequest("invalid-vin", "A VIN has 17 letters and digits (no I, O or Q).");
        }
        DecodedVin cached = cache.get(normalized);
        if (cached != null) {
            return cached;
        }
        Response response;
        try {
            response = vpic.get().uri("/vehicles/DecodeVinValues/{vin}?format=json", normalized).retrieve()
                    .body(Response.class);
        } catch (RuntimeException e) {
            log.warn("VIN decode failed: {}", e.toString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "vin-decoder-unavailable", "Service Unavailable",
                    "The VIN lookup isn't answering right now. Enter the car's details by hand.");
        }
        Result result = response == null || response.results() == null || response.results().isEmpty() ? null
                : response.results().getFirst();
        if (result == null || blank(result.make())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "vin-not-decoded", "Not Found",
                    "This VIN couldn't be decoded. Check it, or enter the car's details by hand.");
        }
        List<String> warnings = new ArrayList<>();
        if (!checkDigitMatches(normalized)) {
            warnings.add("The VIN's check digit doesn't match. Check it for a typo (North American cars).");
        }
        DecodedVin decoded = new DecodedVin(normalized, year(result.modelYear()), name(result.make()),
                cut(text(result.model()), 50), cut(text(blank(result.trim()) ? result.series() : result.trim()), 60),
                cut(engine(result), 80), text(result.bodyClass()), List.copyOf(warnings));
        cache.put(normalized, decoded);
        return decoded;
    }

    /** The 9th character of a North American VIN checks the others (49 CFR 565). */
    static boolean checkDigitMatches(String vin) {
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            char weight = WEIGHTS.charAt(i);
            sum += value(vin.charAt(i)) * (weight == 'X' ? 10 : weight - '0');
        }
        int remainder = sum % 11;
        char expected = remainder == 10 ? 'X' : (char) ('0' + remainder);
        return vin.charAt(8) == expected;
    }

    private static int value(char c) {
        if (Character.isDigit(c)) {
            return c - '0';
        }
        return switch (c) {
            case 'A', 'J' -> 1;
            case 'B', 'K', 'S' -> 2;
            case 'C', 'L', 'T' -> 3;
            case 'D', 'M', 'U' -> 4;
            case 'E', 'N', 'V' -> 5;
            case 'F', 'W' -> 6;
            case 'G', 'P', 'X' -> 7;
            case 'H', 'Y' -> 8;
            case 'R', 'Z' -> 9;
            default -> 0;
        };
    }

    /** "2.0L 4-cyl Gasoline", "Electric", or what is known of it. */
    static String engine(Result r) {
        String fuel = text(r.fuelTypePrimary());
        String electrification = text(r.electrificationLevel());
        if (fuel != null && fuel.toLowerCase(Locale.ROOT).contains("electric") && blank(r.displacementL())) {
            return "Electric";
        }
        List<String> parts = new ArrayList<>();
        if (!blank(r.displacementL())) {
            try {
                parts.add(new BigDecimal(r.displacementL().strip()).setScale(1, RoundingMode.HALF_UP) + "L");
            } catch (NumberFormatException e) {
                // leave it out
            }
        }
        if (!blank(r.engineCylinders())) {
            parts.add(r.engineCylinders().strip() + "-cyl");
        }
        if (electrification != null && electrification.toUpperCase(Locale.ROOT).contains("HEV")) {
            parts.add("Hybrid");
        } else if (fuel != null) {
            parts.add(fuel);
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    /** vPIC writes makes in capitals: "HONDA" → "Honda", "MERCEDES-BENZ" → "Mercedes-Benz", "BMW" stays. */
    static String name(String make) {
        String value = text(make);
        if (value == null || value.length() <= 3 || !value.equals(value.toUpperCase(Locale.ROOT))) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length());
        boolean start = true;
        for (char c : value.toCharArray()) {
            out.append(start ? c : Character.toLowerCase(c));
            start = c == ' ' || c == '-';
        }
        return cut(out.toString(), 50);
    }

    private static Integer year(String value) {
        try {
            int year = Integer.parseInt(value.strip());
            return year >= 1900 && year <= 2100 ? year : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank() || "Not Applicable".equalsIgnoreCase(value.strip());
    }

    private static String text(String value) {
        return blank(value) ? null : value.strip();
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
