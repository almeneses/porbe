package com.porbe.app.operation;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

/** Publica la actividad reciente de administración de operaciones. */
@RestController
@RequestMapping("/api/operation-audit")
public class OperationAuditController {

    private final OperationManagementService managementService;

    public OperationAuditController(OperationManagementService managementService) {
        this.managementService = managementService;
    }

    @GetMapping
    List<OperationAuditResponse> list(@RequestParam(required = false) Long portfolioId) {
        return managementService.auditTrail(portfolioId);
    }
}
