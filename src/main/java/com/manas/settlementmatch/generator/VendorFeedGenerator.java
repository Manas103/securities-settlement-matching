package com.manas.settlementmatch.generator;

import com.manas.settlementmatch.goldencopy.GoldenField;
import com.manas.settlementmatch.goldencopy.IdentifierCrosswalk;
import com.manas.settlementmatch.goldencopy.SecurityIdentifiers;
import com.manas.settlementmatch.goldencopy.Vendor;
import com.manas.settlementmatch.goldencopy.VendorFeedRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Deterministic, seeded synthetic traffic for the four golden-copy vendor
 * feeds: for each security in a given {@link IdentifierCrosswalk}, builds
 * one {@link VendorFeedRecord} per vendor, each keyed on that vendor's own
 * native identifier scheme. Every security starts from agreeing "base
 * facts"; a configurable fraction of (security, field) combinations are
 * then deliberately seeded with a genuine cross-vendor disagreement (one
 * random vendor gets an altered value, the rest keep the base value), and
 * a separate configurable fraction of (security, field, vendor)
 * combinations are independently dropped to {@code null} (a vendor simply
 * not carrying that field), so missingness and disagreement are two
 * distinct, independently-seeded conditions.
 */
public final class VendorFeedGenerator {

    private static final String[] NAMES = {
            "Harrowgate Industrials", "Valebrook Energy", "Coppergate Materials", "Thistlewood Capital",
            "Northfield Logistics", "Sunderland Technologies", "Ashworth Financial", "Marrow Point Utilities",
            "Linden Hollow Retail", "Overbrook Pharmaceuticals"
    };
    private static final String[] COUNTRIES = {"US", "GB", "DE", "FR", "JP"};
    private static final String[] CURRENCIES = {"USD", "GBP", "EUR", "EUR", "JPY"};
    private static final String[] SECTORS = {
            "Industrials", "Energy", "Materials", "Financials", "Technology", "Utilities", "Consumer Discretionary", "Healthcare"
    };
    private static final String[] EXCHANGES = {"NYSE", "NASDAQ", "LSE", "XETRA", "TSE"};

    public record VendorFeedBundle(Map<Vendor, List<VendorFeedRecord>> feedsByVendor) {
    }

    private static final class MutableFields {
        String name;
        String country;
        String currency;
        String sector;
        LocalDate maturity;
        BigDecimal coupon;
        String exchange;

        void set(GoldenField field, Object value) {
            switch (field) {
                case NAME -> name = (String) value;
                case COUNTRY -> country = (String) value;
                case CURRENCY -> currency = (String) value;
                case SECTOR -> sector = (String) value;
                case MATURITY -> maturity = (LocalDate) value;
                case COUPON -> coupon = (BigDecimal) value;
                case EXCHANGE -> exchange = (String) value;
            }
        }
    }

    public VendorFeedBundle generate(IdentifierCrosswalk crosswalk, double missingnessRate, double disagreementRate, long seed) {
        Random random = new Random(seed);
        Map<Vendor, List<VendorFeedRecord>> feeds = new EnumMap<>(Vendor.class);
        for (Vendor vendor : Vendor.values()) {
            feeds.put(vendor, new ArrayList<>(crosswalk.size()));
        }

        int recordCounter = 0;
        for (SecurityIdentifiers ids : crosswalk.all()) {
            int countryIndex = random.nextInt(COUNTRIES.length);
            Map<GoldenField, Object> base = new EnumMap<>(GoldenField.class);
            base.put(GoldenField.NAME, NAMES[random.nextInt(NAMES.length)] + " " + ids.internalKey());
            base.put(GoldenField.COUNTRY, COUNTRIES[countryIndex]);
            base.put(GoldenField.CURRENCY, CURRENCIES[countryIndex]);
            base.put(GoldenField.SECTOR, SECTORS[random.nextInt(SECTORS.length)]);
            base.put(GoldenField.MATURITY, LocalDate.of(2027, 1, 1).plusDays(random.nextInt(365 * 8)));
            base.put(GoldenField.COUPON, BigDecimal.valueOf(random.nextInt(800), 2));
            base.put(GoldenField.EXCHANGE, EXCHANGES[random.nextInt(EXCHANGES.length)]);

            Map<GoldenField, Object> altered = new EnumMap<>(GoldenField.class);
            altered.put(GoldenField.NAME, base.get(GoldenField.NAME) + "-ALT");
            altered.put(GoldenField.COUNTRY, rotate(COUNTRIES, (String) base.get(GoldenField.COUNTRY)));
            altered.put(GoldenField.CURRENCY, rotate(CURRENCIES, (String) base.get(GoldenField.CURRENCY)));
            altered.put(GoldenField.SECTOR, rotate(SECTORS, (String) base.get(GoldenField.SECTOR)));
            altered.put(GoldenField.MATURITY, ((LocalDate) base.get(GoldenField.MATURITY)).plusDays(45));
            altered.put(GoldenField.COUPON, ((BigDecimal) base.get(GoldenField.COUPON)).add(BigDecimal.valueOf(0.125)).setScale(3, RoundingMode.HALF_UP));
            altered.put(GoldenField.EXCHANGE, rotate(EXCHANGES, (String) base.get(GoldenField.EXCHANGE)));

            Map<GoldenField, Vendor> disagreeingVendorByField = new EnumMap<>(GoldenField.class);
            for (GoldenField field : GoldenField.values()) {
                if (random.nextDouble() < disagreementRate) {
                    disagreeingVendorByField.put(field, Vendor.values()[random.nextInt(Vendor.values().length)]);
                }
            }

            for (Vendor vendor : Vendor.values()) {
                MutableFields fields = new MutableFields();
                for (GoldenField field : GoldenField.values()) {
                    Object value = base.get(field);
                    if (disagreeingVendorByField.get(field) == vendor) {
                        value = altered.get(field);
                    }
                    if (random.nextDouble() < missingnessRate) {
                        value = null;
                    }
                    fields.set(field, value);
                }
                String nativeIdentifier = ids.forScheme(vendor.nativeScheme());
                String vendorRecordId = vendor.recordIdPrefix() + "-" + "%08d".formatted(recordCounter++);
                feeds.get(vendor).add(new VendorFeedRecord(
                        vendor, vendorRecordId, nativeIdentifier,
                        fields.name, fields.country, fields.currency, fields.sector,
                        fields.maturity, fields.coupon, fields.exchange));
            }
        }

        return new VendorFeedBundle(feeds);
    }

    private String rotate(String[] values, String current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }
}
