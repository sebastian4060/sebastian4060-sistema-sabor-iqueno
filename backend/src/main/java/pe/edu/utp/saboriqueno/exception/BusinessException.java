package pe.edu.utp.saboriqueno.exception;

/** Indica que una operación incumple una regla de negocio. */
public class BusinessException extends RuntimeException {
  public BusinessException(String message) {
    super(message);
  }
}
