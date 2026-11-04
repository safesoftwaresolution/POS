package com.sciencebot.pos.credit.internal.controllers;

import com.sciencebot.pos.credit.CreditAccountDto;
import com.sciencebot.pos.credit.CreditFacade;
import com.sciencebot.pos.credit.CreditMovementDto;
import com.sciencebot.pos.credit.RegisterPaymentCommand;
import com.sciencebot.pos.credit.SetCreditLimitCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/credit")
@Tag(name = "Fiado", description = "Cuentas de fiado de los clientes: quién debe cuánto y sus abonos")
public class CreditController {

    private final CreditFacade creditFacade;

    public CreditController(CreditFacade creditFacade) {
        this.creditFacade = creditFacade;
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(summary = "Listar cuentas de fiado",
            description = "Clientes con cuenta de fiado, de mayor a menor deuda. `onlyWithBalance=true` (por defecto) "
                    + "muestra solo a quienes deben. Un vendedor ve únicamente los clientes a quienes él les fió.")
    public ResponseEntity<Page<CreditAccountDto>> listAccounts(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "true") boolean onlyWithBalance,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(creditFacade.listAccounts(search, onlyWithBalance, PageRequest.of(page, size)));
    }

    @GetMapping("/accounts/{customerId}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(summary = "Saldo y cupo de un cliente",
            description = "Lo que debe hoy y su cupo. Cualquier rol de venta lo consulta: hace falta para fiarle en el POS.")
    public ResponseEntity<CreditAccountDto> getAccount(@PathVariable Long customerId) {
        return ResponseEntity.ok(creditFacade.getAccount(customerId));
    }

    @GetMapping("/accounts/{customerId}/movements")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(summary = "Movimientos de la cuenta",
            description = "Ventas a fiado, abonos y devoluciones, del más reciente al más antiguo. "
                    + "Un vendedor solo ve cuentas a las que él les fió.")
    public ResponseEntity<Page<CreditMovementDto>> getMovements(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(creditFacade.getMovements(customerId, PageRequest.of(page, size)));
    }

    @PostMapping("/accounts/{customerId}/payments")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(summary = "Registrar un abono",
            description = "Baja la deuda del cliente. No puede superar la deuda ni pagarse con el método CREDIT.")
    public ResponseEntity<CreditMovementDto> registerPayment(@PathVariable Long customerId,
                                                             @RequestBody RegisterPaymentCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(creditFacade.registerPayment(customerId, command));
    }

    @PutMapping("/accounts/{customerId}/limit")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(summary = "Fijar el cupo de un cliente",
            description = "Cupo máximo de deuda. Sin `creditLimit` (null) se quita el límite. Queda auditado.")
    public ResponseEntity<CreditAccountDto> setCreditLimit(@PathVariable Long customerId,
                                                           @RequestBody SetCreditLimitCommand command) {
        return ResponseEntity.ok(creditFacade.setCreditLimit(customerId, command.creditLimit()));
    }
}
