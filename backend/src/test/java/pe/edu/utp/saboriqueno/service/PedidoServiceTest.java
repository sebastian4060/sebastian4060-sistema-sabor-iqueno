package pe.edu.utp.saboriqueno.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import pe.edu.utp.saboriqueno.dao.*;
import pe.edu.utp.saboriqueno.dto.PedidoRequest;
import pe.edu.utp.saboriqueno.exception.BusinessException;
import pe.edu.utp.saboriqueno.model.*;

class PedidoServiceTest {
  PedidoDAO pedidos = mock(PedidoDAO.class);
  CatalogoDAO catalogo = mock(CatalogoDAO.class);
  InventarioDAO inventario = mock(InventarioDAO.class);
  AuditoriaService auditoria = mock(AuditoriaService.class);
  CajaDAO caja = mock(CajaDAO.class);
  PedidoService service = new PedidoService(pedidos, catalogo, inventario, auditoria, caja);
  UsuarioAutenticado user = new UsuarioAutenticado(1, "mozo@test", "Mozo", "Mozo");

  PedidoRequest request(int qty) {
    return new PedidoRequest(
        UUID.randomUUID(), "SALON", 1L, null, null, List.of(new PedidoRequest.Item(1, qty, null)));
  }

  @BeforeEach
  void datos() {
    when(pedidos.porSolicitud(any())).thenReturn(null);
    when(pedidos.pedidoActivo(anyLong())).thenReturn(null);
    when(catalogo.producto(1))
        .thenReturn(new Producto(1, "Plato", "Carta", new BigDecimal("32.00")));
    when(inventario.receta(1))
        .thenReturn(
            List.of(
                Map.of(
                    "id_insumo",
                    1L,
                    "cantidad",
                    new BigDecimal("0.5"),
                    "unidad_medida",
                    "kg",
                    "unidad_stock",
                    "kg")));
  }

  @Test
  void stockInsuficienteNoInsertaPedido() {
    when(inventario.bloquearStock(1)).thenReturn(new BigDecimal("0.2"));
    assertThrows(BusinessException.class, () -> service.crear(request(2), user, "127.0.0.1"));
    verify(pedidos, never()).crear(any(), anyLong(), anyString());
  }

  @Test
  void usaPrecioDelCatalogo() {
    when(inventario.bloquearStock(1)).thenReturn(BigDecimal.TEN);
    when(pedidos.crear(any(), eq(1L), anyString())).thenReturn(4L);
    when(pedidos.detalle(4)).thenReturn(new HashMap<>());
    service.crear(request(2), user, "127.0.0.1");
    verify(pedidos).total(4, new BigDecimal("64.00"));
  }

  @Test
  void agregaProductosAlPedidoExistente() {
    when(pedidos.pedidoActivo(1)).thenReturn(4L);
    when(pedidos.bloquear(4)).thenReturn(Map.of("total_pedido", new BigDecimal("32.00")));
    when(pedidos.detalle(4)).thenReturn(new HashMap<>());
    when(inventario.bloquearStock(1)).thenReturn(BigDecimal.TEN);
    service.crear(request(2), user, "127.0.0.1");
    verify(pedidos, never()).crear(any(), anyLong(), anyString());
    verify(pedidos).total(4, new BigDecimal("96.00"));
    verify(pedidos).marcarNuevosPendientes(4);
  }

  @Test
  void rechazaPedidoVacio() {
    var r = new PedidoRequest(UUID.randomUUID(), "SALON", 1L, null, null, List.of());
    assertThrows(BusinessException.class, () -> service.crear(r, user, "127.0.0.1"));
  }

  @Test
  void rechazaRecetaSinInsumos() {
    when(inventario.receta(1)).thenReturn(List.of());
    assertThrows(BusinessException.class, () -> service.crear(request(1), user, "127.0.0.1"));
  }

  @Test
  void rechazaUnidadesDistintas() {
    when(inventario.receta(1))
        .thenReturn(
            List.of(
                Map.of(
                    "id_insumo",
                    1L,
                    "cantidad",
                    BigDecimal.ONE,
                    "unidad_medida",
                    "g",
                    "unidad_stock",
                    "kg")));
    assertThrows(BusinessException.class, () -> service.crear(request(1), user, "127.0.0.1"));
  }
}
