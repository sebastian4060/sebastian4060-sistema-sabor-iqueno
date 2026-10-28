package pe.edu.utp.saboriqueno.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.edu.utp.saboriqueno.dao.UsuarioDAO;

/** Una cuenta desactivada pierde acceso también en sesiones ya abiertas. */
public class CuentaActivaFilter extends OncePerRequestFilter {
  private final UsuarioDAO usuarios;

  public CuentaActivaFilter(UsuarioDAO usuarios) {
    this.usuarios = usuarios;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (req.getRequestURI().startsWith("/api/")
        && auth != null
        && auth.isAuthenticated()
        && !auth.getName().equals("anonymousUser")) {
      try {
        usuarios.buscarPorEmail(auth.getName());
      } catch (EmptyResultDataAccessException e) {
        var session = req.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        res.setStatus(401);
        res.setContentType("application/json");
        res.getWriter().write("{\"message\":\"La cuenta fue desactivada.\"}");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
