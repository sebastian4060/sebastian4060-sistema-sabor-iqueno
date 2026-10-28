package pe.edu.utp.saboriqueno.controller;

import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.saboriqueno.dao.CatalogoDAO;
import pe.edu.utp.saboriqueno.model.Producto;
import pe.edu.utp.saboriqueno.service.CatalogoService;

@RestController
@RequestMapping("/api")
public class CatalogoController {
  private final CatalogoDAO dao;
  private final CatalogoService service;

  public CatalogoController(CatalogoDAO dao, CatalogoService service) {
    this.dao = dao;
    this.service = service;
  }

  @GetMapping("/productos")
  public List<Producto> productos() {
    return service.productos();
  }

  @GetMapping("/mesas")
  public List<Map<String, Object>> mesas() {
    return dao.mesas();
  }

  @GetMapping("/metodos-pago")
  public List<Map<String, Object>> metodos() {
    return dao.metodos();
  }

  @GetMapping("/insumos")
  @PreAuthorize("hasAnyAuthority('Administrador','Jefe de Cocina')")
  public List<Map<String, Object>> insumos() {
    return dao.insumos();
  }
}
