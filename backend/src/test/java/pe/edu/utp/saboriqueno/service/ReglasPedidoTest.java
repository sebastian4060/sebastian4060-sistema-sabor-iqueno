package pe.edu.utp.saboriqueno.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import pe.edu.utp.saboriqueno.exception.BusinessException;

class ReglasPedidoTest {
  @Test
  void calculaImporteExacto() {
    assertEquals(new BigDecimal("64.00"), ReglasPedido.importe(new BigDecimal("32"), 2));
  }

  @Test
  void rechazaCantidadCero() {
    assertThrows(BusinessException.class, () -> ReglasPedido.importe(BigDecimal.ONE, 0));
  }

  @Test
  void rechazaCantidadExcesiva() {
    assertThrows(BusinessException.class, () -> ReglasPedido.importe(BigDecimal.ONE, 101));
  }

  @Test
  void rechazaPrecioNegativo() {
    assertThrows(BusinessException.class, () -> ReglasPedido.importe(BigDecimal.ONE.negate(), 1));
  }

  @Test
  void descuentaStock() {
    assertEquals(
        new BigDecimal("1.5"), ReglasPedido.descontar(new BigDecimal("2.5"), BigDecimal.ONE));
  }

  @Test
  void impideStockNegativo() {
    assertThrows(
        BusinessException.class, () -> ReglasPedido.descontar(BigDecimal.ONE, BigDecimal.TEN));
  }

  @Test
  void impidePrepararPedidoPagado() {
    assertThrows(
        BusinessException.class, () -> ReglasPedido.transicion("PAGADO", "EN_PREPARACION"));
  }

  @Test
  void permitePrepararRegistrado() {
    assertDoesNotThrow(() -> ReglasPedido.transicion("REGISTRADO", "EN_PREPARACION"));
  }

  @Test
  void impideAnularPreparacion() {
    assertThrows(
        BusinessException.class, () -> ReglasPedido.transicion("EN_PREPARACION", "ANULADO"));
  }
}
