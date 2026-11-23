package com.bayflow.workshop.invoicing;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.security.CurrentUser;
import com.bayflow.workshop.api.WorkshopDtos.PageResponse;
import com.bayflow.workshop.invoicing.InvoiceDtos.EmailRequest;
import com.bayflow.workshop.invoicing.InvoiceDtos.InvoiceResponse;
import com.bayflow.workshop.invoicing.InvoiceDtos.InvoiceSummary;
import com.bayflow.workshop.invoicing.InvoiceDtos.IssueRequest;
import com.bayflow.workshop.invoicing.InvoiceDtos.LineRequest;
import com.bayflow.workshop.invoicing.InvoiceDtos.PaymentRequest;
import com.bayflow.workshop.invoicing.InvoiceDtos.UpdateInvoiceRequest;
import com.bayflow.workshop.invoicing.InvoiceDtos.VoidRequest;
import com.bayflow.workshop.invoicing.InvoiceService.InvoiceFilter;
import com.bayflow.workshop.invoicing.InvoiceService.PaymentCommand;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Invoices. Advisors (and managers) and admins make, edit, issue and take payments; managers and
 * admins refund and void; customers see their own issued invoices. Mechanics have no access.
 */
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Invoices")
public class InvoiceController {

    private static final int MAX_PAGE_SIZE = 100;

    private final InvoiceService invoices;
    private final InvoiceMailService mail;

    public InvoiceController(InvoiceService invoices, InvoiceMailService mail) {
        this.invoices = invoices;
        this.mail = mail;
    }

    @PostMapping("/job-cards/{jobCardId}/invoice")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Draft the invoice of a completed job card",
            description = "Lines from its parts and labour plus the location's fees, with the location's tax rates. "
                    + "409 if the job already has an invoice that isn't void.")
    public InvoiceResponse create(@PathVariable UUID jobCardId, Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        return response(invoices.createDraft(user, jobCardId), user);
    }

    @GetMapping("/job-cards/{jobCardId}/invoice")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR', 'ADMIN')")
    @Operation(summary = "The job card's invoice (404 when it has none, or a customer's is still a draft)")
    public InvoiceResponse forJobCard(@PathVariable UUID jobCardId, Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        return response(invoices.forJobCard(user, jobCardId), user);
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR', 'ADMIN')")
    @Operation(summary = "Invoices, newest first",
            description = "status: DRAFT, ISSUED, PARTIALLY_PAID, PAID, VOID or UNPAID (issued or partly paid); "
                    + "from/to: issue time (creation time for drafts); q: number, customer or registration. "
                    + "Customers get their own issued invoices.")
    public PageResponse<InvoiceSummary> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Authentication auth) {
        return PageResponse.from(invoices.list(CurrentUser.from(auth), new InvoiceFilter(status, from, to, q),
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)), InvoiceSummary::from);
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR', 'ADMIN')")
    @Operation(summary = "One invoice with its lines, taxes and payments")
    public InvoiceResponse get(@PathVariable UUID id, Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        return response(invoices.get(user, id), user);
    }

    @PutMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'ADMIN')")
    @Operation(summary = "Edit a draft: reprice lines, enter part costs, add or remove fees and discounts",
            description = "Send every line in order; parts and labour from the job card can't be removed. "
                    + "Send the version you read (409 if stale).")
    public InvoiceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateInvoiceRequest request,
            Authentication auth) {
        return response(invoices.edit(id, request.version(), request.lines().stream().map(LineRequest::toEdit).toList(),
                request.notes()), CurrentUser.from(auth));
    }

    @DeleteMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Discard a draft (issued invoices are voided instead)")
    public void discard(@PathVariable UUID id) {
        invoices.discard(id);
    }

    @PostMapping("/invoices/{id}/issue")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'ADMIN')")
    @Operation(summary = "Issue a draft: it gets the next number, is locked, and the customer is told")
    public InvoiceResponse issue(@PathVariable UUID id, @Valid @RequestBody IssueRequest request, Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        return response(invoices.issue(user, id, request.version()), user);
    }

    @PostMapping("/invoices/{id}/payments")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'ADMIN')")
    @Operation(summary = "Record a payment taken at the counter (negative = refund, managers and admins)",
            description = "Send an Idempotency-Key header: the same key twice records the payment once.")
    public InvoiceResponse pay(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey, Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        return response(invoices.pay(user, id, new PaymentCommand(request.method(), request.amount(),
                request.reference(), request.receivedAt()), idempotencyKey), user);
    }

    @PostMapping("/invoices/{id}/void")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Void an issued invoice with nothing paid on it; it keeps its number")
    public InvoiceResponse voidInvoice(@PathVariable UUID id, @Valid @RequestBody VoidRequest request,
            Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        return response(invoices.voidInvoice(user, id, request.reason()), user);
    }

    @PostMapping("/invoices/{id}/email")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'ADMIN')")
    @Operation(summary = "E-mail an issued invoice to the customer",
            description = "The whole invoice in the e-mail body, with a link to view it online when the customer "
                    + "has an account. to: blank = the customer's address. 503 when e-mail is not set up.")
    public InvoiceResponse email(@PathVariable UUID id, @Valid @RequestBody(required = false) EmailRequest request,
            Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        return response(mail.email(user, id, request == null ? null : request.to()), user);
    }

    private static InvoiceResponse response(Invoice invoice, CurrentUser user) {
        return InvoiceResponse.from(invoice, !user.isCustomer());
    }
}
