package com.bayflow.workshop.quickbooks;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.security.CurrentUser;
import com.bayflow.security.TenantContext;
import com.bayflow.workshop.config.WorkshopProperties;
import com.bayflow.workshop.invoicing.InvoiceDtos.InvoiceResponse;
import com.bayflow.workshop.quickbooks.QuickBooksApi.QuickBooksException;
import com.bayflow.workshop.quickbooks.QuickBooksService.Choice;
import com.bayflow.workshop.quickbooks.QuickBooksService.Mapping;
import com.bayflow.workshop.quickbooks.QuickBooksService.Status;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * QuickBooks Online: admins connect the organization's company and choose where lines are
 * booked; staff send issued invoices (with their payments and voids) one at a time.
 */
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "QuickBooks")
public class QuickBooksController {

    public record ConnectRequest(@NotBlank @Size(max = 400) String returnUrl) {
    }

    public record ConnectResponse(String authorizeUrl) {
    }

    public record MappingRequest(
            @NotBlank @Size(max = 32) String partsItemId, @Size(max = 200) String partsItemName,
            @NotBlank @Size(max = 32) String labourItemId, @Size(max = 200) String labourItemName,
            @NotBlank @Size(max = 32) String feesItemId, @Size(max = 200) String feesItemName,
            @Size(max = 32) String depositAccountId, @Size(max = 200) String depositAccountName) {

        Mapping toMapping() {
            return new Mapping(partsItemId, partsItemName, labourItemId, labourItemName, feesItemId, feesItemName,
                    depositAccountId, depositAccountName);
        }
    }

    private final QuickBooksService quickbooks;
    private final QuickBooksSync sync;
    private final WorkshopProperties properties;

    public QuickBooksController(QuickBooksService quickbooks, QuickBooksSync sync, WorkshopProperties properties) {
        this.quickbooks = quickbooks;
        this.sync = sync;
        this.properties = properties;
    }

    @GetMapping("/quickbooks")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'ADMIN')")
    @Operation(summary = "Whether QuickBooks is set up on the server, connected and ready to take invoices")
    public Status status() {
        return quickbooks.status();
    }

    @PostMapping("/quickbooks/connect")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Start connecting the organization's QuickBooks company (ADMIN)",
            description = "Returns Intuit's sign-in address; the browser goes there and comes back to returnUrl "
                    + "with ?quickbooks=connected, cancelled, expired or failed.")
    public ConnectResponse connect(@Valid @RequestBody ConnectRequest request, Authentication auth) {
        return new ConnectResponse(quickbooks.startConnect(CurrentUser.from(auth), request.returnUrl()));
    }

    /**
     * Intuit's redirect, the same for every workshop (registered once at Intuit). No sign-in:
     * the one-time state says which organization asked, and only its hash is stored.
     */
    @GetMapping("/quickbooks/callback")
    @Operation(summary = "Intuit sends the browser back here after connecting (no sign-in)")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state, @RequestParam(required = false) String realmId,
            @RequestParam(required = false) String error) {
        Optional<UUID> tenant = QuickBooksService.tenantOf(state);
        Optional<String> next = tenant.flatMap(id -> TenantContext.call(id,
                () -> quickbooks.completeConnect(state, code, realmId, error)));
        String target = next.orElse(stripSlash(properties.getSiteUrl()) + "/?quickbooks=failed");
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, URI.create(target).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @DeleteMapping("/quickbooks/connection")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Disconnect QuickBooks (ADMIN); invoices already sent stay in QuickBooks")
    public void disconnect() {
        quickbooks.disconnect();
    }

    @GetMapping("/quickbooks/items")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "The company's active products and services (ADMIN)")
    public List<Choice> items() {
        try {
            return quickbooks.items();
        } catch (QuickBooksException e) {
            throw QuickBooksService.problem(e);
        }
    }

    @GetMapping("/quickbooks/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Accounts payments can be deposited to: bank and other current assets (ADMIN)")
    public List<Choice> accounts() {
        try {
            return quickbooks.accounts();
        } catch (QuickBooksException e) {
            throw QuickBooksService.problem(e);
        }
    }

    @PutMapping("/quickbooks/mapping")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Which product/service parts, labour and fees are booked to, and the deposit account (ADMIN)")
    public Status mapping(@Valid @RequestBody MappingRequest request) {
        return quickbooks.saveMapping(request.toMapping());
    }

    @PostMapping("/invoices/{id}/quickbooks")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'ADMIN')")
    @Operation(summary = "Send an issued invoice to QuickBooks, with the payments not sent yet (and its void)",
            description = "A QuickBooks error is kept on the invoice and returned in quickbooks.error.")
    public InvoiceResponse send(@PathVariable UUID id, Authentication auth) {
        return InvoiceResponse.from(sync.send(CurrentUser.from(auth), id), true);
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
