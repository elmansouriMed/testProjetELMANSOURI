// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.domain.exception;

public class MessageNotFoundException extends RuntimeException {
  public MessageNotFoundException(long id) {
    super("Le message " + id + " est introuvable.");
  }
}
