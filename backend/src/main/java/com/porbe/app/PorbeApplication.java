package com.porbe.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
/** Punto de entrada y raíz de configuración de la aplicación Porbe. */
public class PorbeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PorbeApplication.class, args);
    }
}
