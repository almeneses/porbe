package com.porbe.app.auth;

import com.porbe.app.importer.DuplicateImportException;
import com.porbe.app.importer.PortfolioImportResult;
import com.porbe.app.importer.PortfolioImportValidationException;
import com.porbe.app.market.MarketDataNotFoundException;
import com.porbe.app.operation.OperationResourceNotFoundException;
import com.porbe.app.operation.OperationValidationException;
import com.porbe.app.operation.OperationValidationResponse;
import com.porbe.app.report.PortfolioReportNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduce excepciones conocidas a respuestas HTTP consistentes y seguras. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ApiErrorResponse badCredentials() {
        return new ApiErrorResponse("CREDENCIALES_INVALIDAS", "El usuario o la contraseña no son correctos.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse validation(MethodArgumentNotValidException exception) {
        var message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Revisa la información enviada.");
        return new ApiErrorResponse("DATOS_INVALIDOS", message);
    }

    @ExceptionHandler(PortfolioImportValidationException.class)
    ResponseEntity<PortfolioImportResult> importValidation(PortfolioImportValidationException exception) {
        return ResponseEntity.unprocessableEntity().body(exception.getResult());
    }

    @ExceptionHandler(DuplicateImportException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiErrorResponse duplicateImport(DuplicateImportException exception) {
        return new ApiErrorResponse("ARCHIVO_DUPLICADO", exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    ApiErrorResponse fileTooLarge() {
        return new ApiErrorResponse("ARCHIVO_MUY_GRANDE", "El archivo no puede superar 5 MB.");
    }

    @ExceptionHandler(MarketDataNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiErrorResponse marketDataNotFound(MarketDataNotFoundException exception) {
        return new ApiErrorResponse("PRECIOS_NO_ENCONTRADOS", exception.getMessage());
    }

    @ExceptionHandler(OperationValidationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    OperationValidationResponse operationValidation(OperationValidationException exception) {
        return new OperationValidationResponse(
                "OPERACION_INVALIDA",
                exception.getMessage(),
                exception.getErrors());
    }

    @ExceptionHandler(OperationResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiErrorResponse operationNotFound(OperationResourceNotFoundException exception) {
        return new ApiErrorResponse("RECURSO_NO_ENCONTRADO", exception.getMessage());
    }

    @ExceptionHandler(PortfolioReportNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiErrorResponse reportNotFound(PortfolioReportNotFoundException exception) {
        return new ApiErrorResponse("INFORME_NO_ENCONTRADO", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse illegalArgument(IllegalArgumentException exception) {
        return new ApiErrorResponse("DATOS_INVALIDOS", exception.getMessage());
    }
}
