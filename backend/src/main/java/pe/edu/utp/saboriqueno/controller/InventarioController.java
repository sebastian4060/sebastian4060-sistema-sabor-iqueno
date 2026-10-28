package pe.edu.utp.saboriqueno.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.saboriqueno.dto.InsumoMovimientoRequest;
import pe.edu.utp.saboriqueno.service.*;

@RestController
@RequestMapping("/api/insumos")
@PreAuthorize("hasAuthority('Administrador')")
public class InventarioController {
  private final InventarioService service;
  private final UsuarioActualService actual;

  public InventarioController(InventarioService service, UsuarioActualService actual) {
    this.service = service;
    this.actual = actual;
  }

  @PostMapping("/{id}/entradas")
  public Map<String, String> entrada(
      @PathVariable long id,
      @Valid @RequestBody InsumoMovimientoRequest r,
      Authentication auth,
      HttpServletRequest http) {
    service.entrada(id, r, actual.obtener(auth), http.getRemoteAddr());
    return Map.of("message", "Entrada registrada.");
  }
}
