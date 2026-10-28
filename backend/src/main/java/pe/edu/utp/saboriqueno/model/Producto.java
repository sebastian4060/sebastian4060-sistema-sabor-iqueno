package pe.edu.utp.saboriqueno.model;

import java.math.BigDecimal;

/** Representa un producto y su precio vigente. */
public record Producto(long id, String nombre, String categoria, BigDecimal precio) {}
