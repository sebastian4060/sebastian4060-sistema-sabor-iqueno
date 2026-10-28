package pe.edu.utp.saboriqueno.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.saboriqueno.dto.PedidoRequest;
import pe.edu.utp.saboriqueno.exception.BusinessException;
import pe.edu.utp.saboriqueno.service.*;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {
  private final PedidoService service;
  private final UsuarioActualService actual;

  public PedidoController(PedidoService service, UsuarioActualService actual) {
    this.service = service;
    this.actual = actual;
  }

  @GetMapping
  @PreAuthorize("hasAnyAuthority('Administrador','Mozo','Cocinero','Jefe de Cocina','Cajero')")
  public List<Map<String, Object>> lista() {
    return service.activos();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('Administrador','Mozo','Cocinero','Jefe de Cocina','Cajero')")
  public Map<String, Object> detalle(@PathVariable long id) {
    return service.detalle(id);
  }

  @PostMapping
  @PreAuthorize("hasAnyAuthority('Administrador','Mozo')")
  public Map<String, Object> crear(
      @Valid @RequestBody PedidoRequest r, Authentication auth, HttpServletRequest http) {
    return service.crear(r, actual.obtener(auth), http.getRemoteAddr());
  }

  public record Estado(
      @NotNull @Pattern(regexp = "EN_PREPARACION|LISTO|SERVIDO|ANULADO") String estado) {}

  @PatchMapping("/{id}/estado")
  @PreAuthorize("hasAnyAuthority('Administrador','Mozo','Cocinero','Jefe de Cocina')")
  public Map<String, Object> estado(
      @PathVariable long id,
      @Valid @RequestBody Estado r,
      Authentication auth,
      HttpServletRequest http) {
    var user = actual.obtener(auth);
    if (!user.rol().equals("Administrador")) {
      boolean permitido =
          user.rol().equals("Mozo")
              ? Set.of("ANULADO", "SERVIDO").contains(r.estado())
              : Set.of("EN_PREPARACION", "LISTO").contains(r.estado());
      if (!permitido) throw new BusinessException("Su rol no permite ese cambio de estado.");
    }
    return service.estado(id, r.estado(), user, http.getRemoteAddr());
  }
}
