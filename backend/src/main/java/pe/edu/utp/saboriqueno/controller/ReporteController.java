package pe.edu.utp.saboriqueno.controller;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.saboriqueno.service.ReporteService;

@RestController
@RequestMapping("/api/reportes")
@PreAuthorize("hasAuthority('Administrador')")
public class ReporteController {
  private final ReporteService service;

  public ReporteController(ReporteService service) {
    this.service = service;
  }

  @GetMapping("/inventario.xlsx")
  public ResponseEntity<byte[]> inventario() {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=inventario.xlsx")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(service.inventario());
  }
}
