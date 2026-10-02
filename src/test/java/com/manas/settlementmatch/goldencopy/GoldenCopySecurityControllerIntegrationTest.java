package com.manas.settlementmatch.goldencopy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hits the real golden-copy REST API through a real, randomly-bound HTTP
 * port on the real Spring Boot context (embedded Kafka is required only
 * because the application context as a whole needs a broker to start, the
 * same reason {@code SettlementMatchingIntegrationTest} declares it; the
 * golden-copy feature itself never touches Kafka).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = {"settlement-instructions-test"})
class GoldenCopySecurityControllerIntegrationTest {

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private GoldenCopySecurityService service;

    @Test
    void getByCusipReturnsTheGoldenRecordWithPerFieldLineage() {
        GoldenSecurityRecord expected = service.all().get(0);
        String cusip = expected.identifiers().cusip();

        ResponseEntity<GoldenSecurityRecordResponse> response = restTemplate.getForEntity(
                "/api/golden-copy/securities/CUSIP/" + cusip, GoldenSecurityRecordResponse.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        GoldenSecurityRecordResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.internalKey()).isEqualTo(expected.internalKey());
        assertThat(body.identifiers().cusip()).isEqualTo(cusip);
        assertThat(body.fields()).hasSize(GoldenField.values().length);

        for (FieldLineageResponse fieldResponse : body.fields()) {
            if ("RESOLVED".equals(fieldResponse.outcome())) {
                assertThat(fieldResponse.sourceVendor()).isNotNull();
                assertThat(fieldResponse.sourceVendorRecordId()).isNotNull();
                assertThat(fieldResponse.heldForOwner()).isNull();
            } else {
                assertThat(fieldResponse.heldForOwner()).isNotNull();
                assertThat(fieldResponse.sourceVendor()).isNull();
            }
        }
    }

    @Test
    void getByUnknownIdentifierReturnsNotFound() {
        ResponseEntity<GoldenSecurityRecordResponse> response = restTemplate.getForEntity(
                "/api/golden-copy/securities/TICKER/ZZZZ", GoldenSecurityRecordResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void listAllReturnsEveryConsolidatedSecurity() {
        ResponseEntity<GoldenSecurityRecordResponse[]> response = restTemplate.getForEntity(
                "/api/golden-copy/securities", GoldenSecurityRecordResponse[].class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(service.all().size());
    }
}
