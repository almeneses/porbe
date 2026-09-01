package com.porbe.app.market;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/market-data")
/** Expone estado, precios almacenados y sincronización de datos de mercado. */
public class MarketDataController {

    private final MarketDataSyncService marketDataService;
    private final MarketDataScheduleService scheduleService;

    public MarketDataController(
            MarketDataSyncService marketDataService,
            MarketDataScheduleService scheduleService) {
        this.marketDataService = marketDataService;
        this.scheduleService = scheduleService;
    }

    @GetMapping
    MarketDataStatusResponse status(@RequestParam(required = false) Long portfolioId) {
        return marketDataService.status(portfolioId);
    }

    @PostMapping("/sync")
    MarketDataSyncResponse sync(@RequestParam(required = false) Long portfolioId) {
        return marketDataService.syncPortfolio(portfolioId);
    }

    @GetMapping("/weekly-closes")
    MarketWeeklyClosesResponse weeklyCloses(@RequestParam(required = false) Long portfolioId) {
        return marketDataService.weeklyCloses(portfolioId);
    }

    @GetMapping("/schedule")
    MarketDataScheduleResponse schedule() {
        return scheduleService.current();
    }

    @PutMapping("/schedule")
    MarketDataScheduleResponse updateSchedule(
            @Valid @RequestBody MarketDataScheduleRequest request,
            Principal principal) {
        return scheduleService.update(request, principal.getName());
    }

    @PutMapping("/{ticker}/sector")
    MarketSectorResponse updateSector(
            @PathVariable
            @Pattern(regexp = "[A-Za-z0-9^][A-Za-z0-9.^=\\-]{0,29}", message = "El ticker no es válido.")
            String ticker,
            @Valid @RequestBody MarketSectorUpdateRequest request) {
        return marketDataService.updateSector(ticker, request.sector());
    }

    @PutMapping(value = "/{ticker}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    MarketTickerIconResponse updateIcon(
            @PathVariable
            @Pattern(regexp = "[A-Za-z0-9^][A-Za-z0-9.^=\\-]{0,29}", message = "El ticker no es válido.")
            String ticker,
            @RequestPart("file") MultipartFile file) {
        return marketDataService.updateIcon(ticker, file);
    }

    @GetMapping("/{ticker}/icon")
    ResponseEntity<byte[]> icon(
            @PathVariable
            @Pattern(regexp = "[A-Za-z0-9^][A-Za-z0-9.^=\\-]{0,29}", message = "El ticker no es válido.")
            String ticker) {
        var icon = marketDataService.icon(ticker);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(icon.contentType()))
                .contentLength(icon.content().length)
                .body(icon.content());
    }

    @DeleteMapping("/{ticker}/icon")
    MarketTickerIconResponse removeIcon(
            @PathVariable
            @Pattern(regexp = "[A-Za-z0-9^][A-Za-z0-9.^=\\-]{0,29}", message = "El ticker no es válido.")
            String ticker) {
        return marketDataService.removeIcon(ticker);
    }

    @GetMapping("/{ticker}/daily")
    TickerPricesResponse prices(
            @PathVariable
            @Pattern(regexp = "[A-Za-z0-9^][A-Za-z0-9.^=\\-]{0,29}", message = "El ticker no es válido.")
            String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return marketDataService.prices(ticker, from, to);
    }
}
