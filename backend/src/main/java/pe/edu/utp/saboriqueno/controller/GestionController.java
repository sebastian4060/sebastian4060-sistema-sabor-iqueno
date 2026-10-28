package pe.edu.utp.saboriqueno.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.saboriqueno.dao.GestionDAO;
import pe.edu.utp.saboriqueno.dto.GestionRequests.*;
import pe.edu.utp.saboriqueno.service.*;

@RestController
@RequestMapping("/api/gestion")
@PreAuthorize("hasAuthority('Administrador')")
public class GestionController {
  private final GestionDAO dao;
  private final GestionService service;
  private final UsuarioActualService actual;

  public GestionController(GestionDAO dao, GestionService service, UsuarioActualService actual) {
    this.dao = dao;
    this.service = service;
    this.actual = actual;
  }

  @GetMapping
  public Map<String, Object> datos() {
    return dao.datos();
  }

  @GetMapping("/reportes")
  public Map<String, Object> reportes() {
    return dao.reportes();
  }

  @PostMapping("/insumos/{id}/movimientos")
  public void movimiento(
      @PathVariable long id,
      @Valid @RequestBody Movimiento r,
      Authentication a,
      HttpServletRequest h) {
    service.movimiento(id, r, actual.obtener(a), h.getRemoteAddr());
  }

  @PostMapping("/compras")
  public Map<String, Long> compra(
      @Valid @RequestBody Compra r, Authentication a, HttpServletRequest h) {
    return Map.of("id_compra", service.compra(r, actual.obtener(a), h.getRemoteAddr()));
  }

  @PostMapping("/proveedores")
  public void proveedor(@Valid @RequestBody Proveedor r, Authentication a, HttpServletRequest h) {
    service.proveedor(r, actual.obtener(a), h.getRemoteAddr());
  }

  @PostMapping("/usuarios")
  public void usuario(@Valid @RequestBody NuevoUsuario r, Authentication a, HttpServletRequest h) {
    service.usuario(r, actual.obtener(a), h.getRemoteAddr());
  }

  @PatchMapping("/usuarios/{id}")
  public void activo(
      @PathVariable long id, @Valid @RequestBody Activo r, Authentication a, HttpServletRequest h) {
    service.activo(id, r.activo(), actual.obtener(a), h.getRemoteAddr());
  }

  @PostMapping("/personal")
  public void personal(@Valid @RequestBody Persona r, Authentication a, HttpServletRequest h) {
    service.personal(r, actual.obtener(a), h.getRemoteAddr());
  }

  @PutMapping("/personal/{id}/turno")
  public void turno(
      @PathVariable long id, @Valid @RequestBody Turno r, Authentication a, HttpServletRequest h) {
    service.turno(id, r.turnoId(), actual.obtener(a), h.getRemoteAddr());
  }

  @PutMapping("/productos/{id}/receta")
  public void receta(
      @PathVariable long id, @Valid @RequestBody Receta r, Authentication a, HttpServletRequest h) {
    service.receta(id, r, actual.obtener(a), h.getRemoteAddr());
  }

  @PatchMapping("/productos/{id}/precio")
  public void precio(
      @PathVariable long id, @Valid @RequestBody Precio r, Authentication a, HttpServletRequest h) {
    service.precio(id, r.precio(), actual.obtener(a), h.getRemoteAddr());
  }
}
