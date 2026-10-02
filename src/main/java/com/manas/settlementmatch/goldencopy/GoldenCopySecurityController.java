package com.manas.settlementmatch.goldencopy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves the golden-copy security master. For each golden field, the
 * response names which vendor's own record id supplied the value
 * (lineage), or, if the survivorship rules could not resolve it, the named
 * data owner it is held for. OpenAPI documentation is generated from these
 * annotations by springdoc and served at {@code /v3/api-docs} and
 * {@code /swagger-ui.html}; this repository has no hand-written openapi.yaml.
 */
@RestController
@RequestMapping("/api/golden-copy/securities")
@Tag(name = "Golden-Copy Security Master", description = "Field-level survivorship resolution and lineage over four disagreeing vendor reference feeds")
public class GoldenCopySecurityController {

    private final GoldenCopySecurityService service;

    public GoldenCopySecurityController(GoldenCopySecurityService service) {
        this.service = service;
    }

    @Operation(summary = "Look up the golden security record by any one of its four identifier schemes",
            description = "scheme is one of CUSIP, ISIN, SEDOL, TICKER. Each field in the response names the "
                    + "vendor record id that supplied its value, or the named data owner it is held for.")
    @GetMapping("/{scheme}/{identifier}")
    public ResponseEntity<GoldenSecurityRecordResponse> getByIdentifier(
            @PathVariable IdentifierScheme scheme, @PathVariable String identifier) {
        return service.findBySchemeAndIdentifier(scheme, identifier)
                .map(record -> ResponseEntity.ok(GoldenSecurityRecordResponse.from(record)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "List every golden security record currently consolidated in memory")
    @GetMapping
    public List<GoldenSecurityRecordResponse> listAll() {
        return service.all().stream().map(GoldenSecurityRecordResponse::from).toList();
    }
}
