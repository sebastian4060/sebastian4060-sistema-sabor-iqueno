package pe.edu.utp.saboriqueno.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import pe.edu.utp.saboriqueno.dao.*;
import pe.edu.utp.saboriqueno.dto.PagoRequest;
import pe.edu.utp.saboriqueno.exception.BusinessException;
import pe.edu.utp.saboriqueno.model.UsuarioAutenticado;

class CajaServiceTest {
  @org.junit.jupiter.api.BeforeEach
  void datos() {
    when(caja.pagoPrevio(any())).thenReturn(null);
    when(pedidos.detalle(anyLong())).thenReturn(Map.of());
  }

  PedidoDAO pedidos = mock(PedidoDAO.class);
  CajaDAO caja = mock(CajaDAO.class);
  AuditoriaService auditoria = mock(AuditoriaService.class);
  CajaService service = new CajaService(pedidos, caja, auditoria);
  UsuarioAutenticado user = new UsuarioAutenticado(2, "caja@test", "Caja", "Cajero");
  PagoRequest request = new PagoRequest(UUID.randomUUID(), "Efectivo", "", null);

  @Test
  void rechazaCobroDuplicado() {
    when(pedidos.bloquear(1)).thenReturn(Map.of("estado", "PAGADO"));
    assertThrows(BusinessException.class, () -> service.cobrar(1, request, user, "127.0.0.1"));
    verify(caja, never()).cobrar(anyLong(), any(), any(), anyLong(), any(), anyString());
  }

  @Test
  void rechazaCobroAntesDePreparar() {
    when(pedidos.bloquear(1)).thenReturn(Map.of("estado", "REGISTRADO"));
    assertThrows(BusinessException.class, () -> service.cobrar(1, request, user, "127.0.0.1"));
  }

  @Test
  void cierraPedidoYLiberaMesa() {
    when(pedidos.bloquear(1))
        .thenReturn(
            Map.of("estado", "LISTO", "total_pedido", new BigDecimal("64.00"), "id_mesa", 3L));
    when(caja.cobrar(anyLong(), any(), any(), anyLong(), any(), anyString()))
        .thenReturn(Map.of("id_pago", 7));
    service.cobrar(1, request, user, "127.0.0.1");
    verify(pedidos).cambiarEstado(1, "PAGADO");
    verify(caja).liberarMesa(3L);
  }
}
