package com.sciencebot.pos.billing.internal.controllers;

import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.dto.UpdateNumberingRangeCurrentRequest;
import com.sciencebot.pos.billing.internal.controllers.doc.BillingNumberingRangeApiDoc;
import com.sciencebot.pos.billing.internal.services.NumberingRangeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/billing/numbering-ranges")
public class BillingNumberingRangeController implements BillingNumberingRangeApiDoc {

    private final NumberingRangeService numberingRangeService;

    public BillingNumberingRangeController(NumberingRangeService numberingRangeService) {
        this.numberingRangeService = numberingRangeService;
    }

    @Override
    @GetMapping("/dian")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    public ResponseEntity<List<NumberingRangeDto>> getDianNumberingRanges() {
        return ResponseEntity.ok(numberingRangeService.queryDianNumberingRanges());
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    public ResponseEntity<List<NumberingRangeDto>> listNumberingRanges() {
        return ResponseEntity.ok(numberingRangeService.listNumberingRanges());
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    public ResponseEntity<NumberingRangeDto> getNumberingRange(@PathVariable Long id) {
        NumberingRangeDto dto = numberingRangeService.getNumberingRange(id);
        if (dto != null) {
            return ResponseEntity.ok(dto);
        }
        return ResponseEntity.notFound().build();
    }

    @Override
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<NumberingRangeDto> createNumberingRange(@Valid @RequestBody CreateNumberingRangeRequest request) {
        NumberingRangeDto created = numberingRangeService.createNumberingRange(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<Void> deleteNumberingRange(@PathVariable Long id) {
        numberingRangeService.deleteNumberingRange(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<NumberingRangeDto> toggleNumberingRangeStatus(@PathVariable Long id) {
        return ResponseEntity.ok(numberingRangeService.toggleNumberingRangeStatus(id));
    }

    @Override
    @PatchMapping("/{id}/current")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<NumberingRangeDto> updateNumberingRangeCurrent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateNumberingRangeCurrentRequest request) {
        return ResponseEntity.ok(numberingRangeService.updateNumberingRangeCurrent(id, request.current()));
    }

    @Override
    @PostMapping("/{id}/set-default")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<NumberingRangeDto> setDefaultNumberingRange(@PathVariable Long id) {
        return ResponseEntity.ok(numberingRangeService.setDefaultNumberingRange(id));
    }
}
