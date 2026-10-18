// createdBy ELMANSOURI Mohamed
package fr.bank.payments.shared.exception;

import fr.bank.payments.messages.domain.exception.InvalidMessageSearchException;
import fr.bank.payments.messages.domain.exception.MessageNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(InvalidMessageSearchException.class)
  ResponseEntity<Object> invalidSearch(InvalidMessageSearchException exception) {
    return problem(
        HttpStatus.BAD_REQUEST, "Recherche invalide", exception.getMessage(), new HttpHeaders());
  }

  @ExceptionHandler(MessageNotFoundException.class)
  ResponseEntity<Object> notFound(MessageNotFoundException exception) {
    return problem(
        HttpStatus.NOT_FOUND, "Message introuvable", exception.getMessage(), new HttpHeaders());
  }

  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return invalidParameters(headers);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return status.is5xxServerError() ? unexpected(exception) : invalidParameters(headers);
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<Object> concurrentUpdate(OptimisticLockingFailureException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "Modification concurrente",
        "L'archive a été modifiée par une autre transaction. Relisez-la avant de réessayer.",
        new HttpHeaders());
  }

  @ExceptionHandler({DataAccessException.class, TransactionException.class})
  ResponseEntity<Object> unavailable(RuntimeException exception) {
    var headers = new HttpHeaders();
    headers.set(HttpHeaders.RETRY_AFTER, "5");
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Stockage indisponible",
        "Le stockage est temporairement indisponible. Réessayez ultérieurement.",
        headers);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> unexpected(Exception exception) {
    LOG.error("Erreur inattendue pendant la consultation des messages", exception);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Erreur interne",
        "Une erreur inattendue est survenue.",
        new HttpHeaders());
  }

  private ResponseEntity<Object> invalidParameters(HttpHeaders headers) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "Paramètres invalides",
        "Vérifiez le format et les limites des paramètres de recherche.",
        headers);
  }

  private ResponseEntity<Object> problem(
      HttpStatus status, String title, String detail, HttpHeaders headers) {
    var body = ProblemDetail.forStatusAndDetail(status, detail);
    body.setTitle(title);
    return new ResponseEntity<>(body, headers, status);
  }
}
