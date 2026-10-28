package pe.edu.utp.saboriqueno.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.saboriqueno.dao.CajaDAO;
import pe.edu.utp.saboriqueno.dto.PagoRequest;
import pe.edu.utp.saboriqueno.service.*;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyAuthority('Administrador','Cajero')")
public class CajaController {
  private final CajaService service;
  private final UsuarioActualService actual;
  private final CajaDAO dao;

  public CajaController(CajaService service, UsuarioActualService actual, CajaDAO dao) {
    this.service = service;
    this.actual = actual;
    this.dao = dao;
  }

  @PostMapping("/pedidos/{id}/pago")
  public Map<String, Object> pago(
      @PathVariable long id,
      @Valid @RequestBody PagoRequest r,
      Authentication auth,
      HttpServletRequest http) {
    return service.cobrar(id, r, actual.obtener(auth), http.getRemoteAddr());
  }

  @GetMapping("/ventas")
  public List<Map<String, Object>> ventas() {
    return dao.ventas();
  }
}
