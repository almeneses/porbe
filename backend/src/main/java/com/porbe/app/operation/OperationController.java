package com.porbe.app.operation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations")
/** Expone el libro de movimientos importados del portafolio principal. */
public class OperationController {

    private final PortfolioOperationRepository operationRepository;

    public OperationController(PortfolioOperationRepository operationRepository) {
        this.operationRepository = operationRepository;
    }

    @GetMapping
    OperationsResponse list() {
        var operations = operationRepository.findTop200ByOrderByDateDescIdDesc().stream()
                .map(OperationResponse::from)
                .toList();
        return new OperationsResponse(operationRepository.count(), operations);
    }
}
