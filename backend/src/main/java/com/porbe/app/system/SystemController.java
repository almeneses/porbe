package com.porbe.app.system;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
/** Publica información mínima de salud e identidad del backend. */
public class SystemController {

    @GetMapping("/status")
    SystemStatusResponse status() {
        return new SystemStatusResponse("Porbe", "disponible", Instant.now());
    }

    /** Estado mínimo serializado por el endpoint de sistema. */
    record SystemStatusResponse(String application, String status, Instant timestamp) {
    }
}
