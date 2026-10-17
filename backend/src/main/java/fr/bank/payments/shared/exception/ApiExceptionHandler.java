// createdBy ELMANSOURI Mohamed
package fr.bank.payments.shared.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> invalidSearch(IllegalArgumentException exception) {
    return ResponseEntity.badRequest()
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage()));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ProblemDetail> unavailable(DataAccessException ex) {
    var problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Le stockage est temporairement indisponible. Réessayez ultérieurement.");
    problem.setTitle("Stockage indisponible");
    return ResponseEntity.status(503).header(HttpHeaders.RETRY_AFTER, "5").body(problem);
  }
}
