package pe.edu.utp.saboriqueno.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/** Protege las operaciones con sesión, permisos y token contra solicitudes cruzadas. */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  private final pe.edu.utp.saboriqueno.dao.UsuarioDAO usuarios;

  public SecurityConfig(pe.edu.utp.saboriqueno.dao.UsuarioDAO usuarios) {
    this.usuarios = usuarios;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.addFilterAfter(
            new pe.edu.utp.saboriqueno.security.CuentaActivaFilter(usuarios),
            org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/",
                        "/index.html",
                        "/login.html",
                        "/css/**",
                        "/js/**",
                        "/api/csrf",
                        "/error",
                        "/favicon.ico")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            f ->
                f.loginProcessingUrl("/api/login")
                    .usernameParameter("email")
                    .successHandler(
                        (req, res, auth) -> {
                          res.setContentType("application/json");
                          res.getWriter().write("{\"ok\":true}");
                        })
                    .failureHandler(
                        (req, res, e) -> {
                          res.setStatus(401);
                          res.setContentType("application/json");
                          res.getWriter().write("{\"message\":\"Credenciales invalidas\"}");
                        }))
        .logout(
            l ->
                l.logoutUrl("/api/logout")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, x) -> {
                          res.setStatus(401);
                          res.setContentType("application/json");
                          res.getWriter().write("{\"message\":\"Inicie sesion\"}");
                        })
                    .accessDeniedHandler(
                        (req, res, x) -> {
                          res.setStatus(403);
                          res.setContentType("application/json");
                          res.getWriter()
                              .write("{\"message\":\"Operacion no permitida o sesion expirada\"}");
                        }))
        .build();
  }
}
