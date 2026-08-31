package com.porbe.app.operation;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

/** Permite consultar y revertir importaciones completas. */
@RestController
@RequestMapping("/api/operation-batches")
public class OperationBatchController {

    private final OperationManagementService managementService;

    public OperationBatchController(OperationManagementService managementService) {
        this.managementService = managementService;
    }

    @GetMapping
    List<OperationBatchResponse> list() {
        return managementService.importedBatches();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revert(@PathVariable Long id, Authentication authentication) {
        managementService.revertBatch(id, authentication.getName());
    }
}
