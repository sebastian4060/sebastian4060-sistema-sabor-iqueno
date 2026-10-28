package pe.edu.utp.saboriqueno.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.saboriqueno.model.UsuarioAutenticado;
import pe.edu.utp.saboriqueno.service.UsuarioActualService;

@RestController
@RequestMapping("/api")
public class AuthController {
  private final UsuarioActualService actual;

  public AuthController(UsuarioActualService actual) {
    this.actual = actual;
  }

  @GetMapping("/csrf")
  public CsrfToken csrf(CsrfToken token) {
    return token;
  }

  @GetMapping("/me")
  public UsuarioAutenticado me(Authentication auth) {
    return actual.obtener(auth);
  }
}
