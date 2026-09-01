package com.porbe.app.portfolio;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso persistente a los portafolios definidos en la aplicación. */
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    Optional<Portfolio> findFirstByNameOrderByIdAsc(String name);

    List<Portfolio> findAllByOrderByCreatedAtAscIdAsc();

    boolean existsByNameIgnoreCase(String name);
}
