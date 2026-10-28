package pe.edu.utp.saboriqueno.model;

import java.io.Serializable;

/** Conserva únicamente los datos necesarios para identificar una sesión. */
public record UsuarioAutenticado(long id, String email, String nombre, String rol)
    implements Serializable {}
