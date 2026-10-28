package pe.edu.utp.saboriqueno.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import pe.edu.utp.saboriqueno.exception.BusinessException;

/** Evalúa importes y transiciones sin depender de la base de datos. */
public final class ReglasPedido {
  private ReglasPedido() {}

  public static BigDecimal importe(BigDecimal precio, int cantidad) {
    if (precio == null || precio.signum() < 0 || cantidad < 1 || cantidad > 100)
      throw new BusinessException("Precio o cantidad inválidos.");
    return precio.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
  }

  public static BigDecimal descontar(BigDecimal stock, BigDecimal cantidad) {
    if (cantidad.signum() <= 0 || stock.compareTo(cantidad) < 0)
      throw new BusinessException("Stock insuficiente.");
    return stock.subtract(cantidad);
  }

  public static void transicion(String actual, String nuevo) {
    boolean ok =
        switch (actual) {
          case "REGISTRADO" -> Set.of("EN_PREPARACION", "ANULADO").contains(nuevo);
          case "EN_PREPARACION" -> nuevo.equals("LISTO");
          case "LISTO" -> nuevo.equals("SERVIDO");
          default -> false;
        };
    if (!ok)
      throw new BusinessException("Cambio de estado no permitido: " + actual + " → " + nuevo);
  }
}
