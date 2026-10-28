-- EL SABOR IQUEÑO: esquema completo con actualización v2 y datos de demostración.
-- Ejecutar COMPLETO una sola vez en una base de datos nueva y vacía.
-- En pgAdmin: seleccionar la base, abrir Query Tool, cargar este archivo y ejecutar.
-- Incluye los cambios de actualizar_v2.sql; no es necesario ejecutarlo por separado.
-- No elimina tablas ni datos existentes; si las tablas ya existen, la creación falla.
-- Todas las operaciones se confirman juntas; ante un error, ejecutar ROLLBACK.
-- El descuento de stock y el cálculo de cuentas los realiza el backend en sus transacciones.

-- =====================================================================
-- EL SABOR IQUEÑO - Sistema de Gestión de Restaurantes
-- Modelo de datos (PostgreSQL 14+) - VERSIÓN CORREGIDA
-- Corrección principal:
--   * Los índices únicos parciales (ux_usuario_email, ux_mesa_numero)
--     dependen de la columna eliminado_en, que se crea en el bloque DO.
--     Ahora se crean DESPUÉS de ese bloque.
-- Los 7 campos de auditoría:
--   creado_en, creado_por, actualizado_en, actualizado_por,
--   eliminado_en, eliminado_por, activo
-- =====================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- crypt() / gen_salt() para contraseñas

-- ============================ SEGURIDAD ==============================
CREATE TABLE rol (
  id_rol       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre       VARCHAR(50) NOT NULL UNIQUE,
  descripcion  TEXT
);

CREATE TABLE usuario (
  id_usuario     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_rol         BIGINT NOT NULL REFERENCES rol(id_rol),
  nombre         VARCHAR(100) NOT NULL,
  apellido       VARCHAR(100) NOT NULL,
  email          VARCHAR(100) NOT NULL,
  password_hash  VARCHAR(255) NOT NULL,   -- crypt(pass, gen_salt('bf'))
  telefono       VARCHAR(20),
  ultimo_login   TIMESTAMP
);

-- Bitácora automática (la llena el trigger; no lleva campos de auditoría propios)
CREATE TABLE auditoria (
  id_auditoria    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_usuario      BIGINT REFERENCES usuario(id_usuario),
  tabla_afectada  VARCHAR(100) NOT NULL,
  accion          VARCHAR(10)  NOT NULL CHECK (accion IN ('INSERT','UPDATE','DELETE')),
  registro_id     BIGINT,
  datos_antes     JSONB,
  datos_despues   JSONB,
  fecha_hora      TIMESTAMP NOT NULL DEFAULT now(),
  ip_origen       VARCHAR(45)
);

-- ============================ CATÁLOGO ===============================
CREATE TABLE categoria_producto (
  id_categoria_producto BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre       VARCHAR(50) NOT NULL UNIQUE,
  descripcion  TEXT
);

CREATE TABLE producto (
  id_producto            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_categoria_producto  BIGINT NOT NULL REFERENCES categoria_producto(id_categoria_producto),
  nombre        VARCHAR(100) NOT NULL,
  descripcion   TEXT,
  precio_venta  NUMERIC(10,2) NOT NULL CHECK (precio_venta >= 0),
  imagen_url    VARCHAR(255)
);

CREATE TABLE receta (
  id_receta    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_producto  BIGINT NOT NULL UNIQUE REFERENCES producto(id_producto),  -- 1 producto = 1 receta
  descripcion  TEXT
);

-- ============================ INVENTARIO =============================
CREATE TABLE categoria_insumo (
  id_categoria_insumo BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre       VARCHAR(50) NOT NULL UNIQUE,
  descripcion  TEXT
);

CREATE TABLE insumo (
  id_insumo            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_categoria_insumo  BIGINT NOT NULL REFERENCES categoria_insumo(id_categoria_insumo),
  nombre           VARCHAR(100) NOT NULL,
  unidad_medida    VARCHAR(20)  NOT NULL,
  stock_actual     NUMERIC(10,3) NOT NULL DEFAULT 0 CHECK (stock_actual >= 0),
  stock_minimo     NUMERIC(10,3) NOT NULL DEFAULT 0 CHECK (stock_minimo >= 0),
  precio_unitario  NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (precio_unitario >= 0)
);

CREATE TABLE receta_detalle (
  id_receta_detalle BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_receta   BIGINT NOT NULL REFERENCES receta(id_receta),
  id_insumo   BIGINT NOT NULL REFERENCES insumo(id_insumo),
  cantidad        NUMERIC(10,3) NOT NULL CHECK (cantidad > 0),
  unidad_medida   VARCHAR(20)   NOT NULL,
  UNIQUE (id_receta, id_insumo)
);

CREATE TABLE proveedor (
  id_proveedor BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  ruc        VARCHAR(11)  NOT NULL UNIQUE,
  nombre     VARCHAR(100) NOT NULL,
  telefono   VARCHAR(20),
  email      VARCHAR(100),
  direccion  TEXT
);

CREATE TABLE compra (
  id_compra    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_proveedor BIGINT NOT NULL REFERENCES proveedor(id_proveedor),
  id_usuario   BIGINT NOT NULL REFERENCES usuario(id_usuario),
  fecha_compra DATE NOT NULL DEFAULT CURRENT_DATE,
  documento    VARCHAR(80) NOT NULL DEFAULT '',
  solicitud_id UUID,
  solicitud_hash VARCHAR(64),
  total        NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
  estado       VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
               CHECK (estado IN ('PENDIENTE','RECIBIDA','ANULADA'))
);

CREATE TABLE detalle_compra (
  id_detalle_compra BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compra  BIGINT NOT NULL REFERENCES compra(id_compra),
  id_insumo  BIGINT NOT NULL REFERENCES insumo(id_insumo),
  cantidad         NUMERIC(10,3) NOT NULL CHECK (cantidad > 0),
  precio_unitario  NUMERIC(10,2) NOT NULL CHECK (precio_unitario >= 0),
  subtotal         NUMERIC(10,2) GENERATED ALWAYS AS (cantidad * precio_unitario) STORED
);

-- ============================ OPERACIÓN ==============================
CREATE TABLE mesa (
  id_mesa    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  numero     INT NOT NULL CHECK (numero > 0),
  capacidad  INT NOT NULL CHECK (capacidad > 0),
  ubicacion  VARCHAR(50),
  estado     VARCHAR(20) NOT NULL DEFAULT 'LIBRE'
             CHECK (estado IN ('LIBRE','OCUPADA','RESERVADA','MANTENIMIENTO'))
);

CREATE TABLE estado_pedido (
  id_estado_pedido BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre       VARCHAR(30) NOT NULL UNIQUE,   -- REGISTRADO, EN_PREPARACION, LISTO, SERVIDO, PAGADO, ANULADO
  descripcion  TEXT,
  orden        INT NOT NULL
);

CREATE TABLE pedido (
  id_pedido         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_mesa           BIGINT REFERENCES mesa(id_mesa),           -- NULL = para llevar / delivery
  id_usuario_mozo   BIGINT NOT NULL REFERENCES usuario(id_usuario),
  id_estado_pedido  BIGINT NOT NULL REFERENCES estado_pedido(id_estado_pedido),
  tipo_pedido       VARCHAR(20) NOT NULL DEFAULT 'SALON'
                    CHECK (tipo_pedido IN ('SALON','LLEVAR','DELIVERY')),
  fecha_hora        TIMESTAMP NOT NULL DEFAULT now(),
  observacion       TEXT,
  total_pedido      NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (total_pedido >= 0),
  CHECK (tipo_pedido <> 'SALON' OR id_mesa IS NOT NULL)
);

CREATE TABLE detalle_pedido (
  id_detalle_pedido BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_pedido    BIGINT NOT NULL REFERENCES pedido(id_pedido),
  id_producto  BIGINT NOT NULL REFERENCES producto(id_producto),
  cantidad         INT NOT NULL CHECK (cantidad > 0),
  precio_unitario  NUMERIC(10,2) NOT NULL CHECK (precio_unitario >= 0),
  subtotal         NUMERIC(10,2) GENERATED ALWAYS AS (cantidad * precio_unitario) STORED,
  estado_cocina    VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
                   CHECK (estado_cocina IN ('PENDIENTE','PREPARANDO','LISTO','ENTREGADO','ANULADO')),
  observacion      TEXT
);

CREATE TABLE notificacion_cocina (
  id_notificacion BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_pedido    BIGINT NOT NULL REFERENCES pedido(id_pedido),
  mensaje      TEXT NOT NULL,
  fecha_envio  TIMESTAMP NOT NULL DEFAULT now(),
  leida        BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE movimiento_inventario (
  id_movimiento     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_insumo         BIGINT NOT NULL REFERENCES insumo(id_insumo),
  id_usuario        BIGINT NOT NULL REFERENCES usuario(id_usuario),
  id_detalle_compra BIGINT REFERENCES detalle_compra(id_detalle_compra),  -- origen: compra
  id_detalle_pedido BIGINT REFERENCES detalle_pedido(id_detalle_pedido),  -- origen: consumo
  tipo_movimiento   VARCHAR(20) NOT NULL CHECK (tipo_movimiento IN ('ENTRADA','SALIDA','AJUSTE','MERMA')),
  cantidad          NUMERIC(10,3) NOT NULL CHECK (cantidad > 0),
  stock_anterior    NUMERIC(10,3) NOT NULL,
  stock_resultante  NUMERIC(10,3) NOT NULL,
  fecha_movimiento  TIMESTAMP NOT NULL DEFAULT now(),
  motivo            TEXT
);

-- ============================ VENTAS =================================
CREATE TABLE metodo_pago (
  id_metodo_pago BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre       VARCHAR(50) NOT NULL UNIQUE,   -- Efectivo, Yape, Plin, Tarjeta...
  descripcion  TEXT
);

CREATE TABLE cuenta (
  id_cuenta   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_pedido   BIGINT NOT NULL REFERENCES pedido(id_pedido),
  id_mesa     BIGINT REFERENCES mesa(id_mesa),
  id_usuario_cajero BIGINT REFERENCES usuario(id_usuario),
  fecha_hora  TIMESTAMP NOT NULL DEFAULT now(),
  subtotal    NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
  descuento   NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (descuento >= 0),
  total       NUMERIC(10,2) GENERATED ALWAYS AS (subtotal - descuento) STORED,
  estado      VARCHAR(20) NOT NULL DEFAULT 'ABIERTA'
              CHECK (estado IN ('ABIERTA','PAGADA','ANULADA')),
  CHECK (descuento <= subtotal)
);

CREATE TABLE detalle_cuenta (
  id_detalle_cuenta BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_cuenta    BIGINT NOT NULL REFERENCES cuenta(id_cuenta),
  id_producto  BIGINT NOT NULL REFERENCES producto(id_producto),
  cantidad         INT NOT NULL CHECK (cantidad > 0),
  precio_unitario  NUMERIC(10,2) NOT NULL CHECK (precio_unitario >= 0),
  subtotal         NUMERIC(10,2) GENERATED ALWAYS AS (cantidad * precio_unitario) STORED
);

CREATE TABLE pago (
  id_pago         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_cuenta       BIGINT NOT NULL REFERENCES cuenta(id_cuenta),
  id_metodo_pago  BIGINT NOT NULL REFERENCES metodo_pago(id_metodo_pago),
  monto           NUMERIC(10,2) NOT NULL CHECK (monto > 0),
  fecha_hora      TIMESTAMP NOT NULL DEFAULT now(),
  referencia      VARCHAR(50),
  estado          VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADO'
                  CHECK (estado IN ('PENDIENTE','CONFIRMADO','ANULADO'))
);

CREATE TABLE comprobante (
  id_comprobante  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_cuenta       BIGINT NOT NULL REFERENCES cuenta(id_cuenta),
  tipo            VARCHAR(20) NOT NULL CHECK (tipo IN ('BOLETA','FACTURA','TICKET')),
  numero_serie    VARCHAR(10) NOT NULL,
  numero_correlativo VARCHAR(20) NOT NULL,
  fecha_emision   TIMESTAMP NOT NULL DEFAULT now(),
  pdf_url         VARCHAR(255),
  estado          VARCHAR(20) NOT NULL DEFAULT 'EMITIDO'
                  CHECK (estado IN ('EMITIDO','ANULADO')),
  UNIQUE (tipo, numero_serie, numero_correlativo)
);

-- ============================ PERSONAL Y TURNOS ======================
CREATE TABLE turno (
  id_turno    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre      VARCHAR(30) NOT NULL UNIQUE,
  hora_inicio TIME NOT NULL,
  hora_fin    TIME NOT NULL
);

CREATE TABLE personal (
  id_personal BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_usuario  BIGINT UNIQUE REFERENCES usuario(id_usuario),   -- opcional: personal con acceso al sistema
  nombre      VARCHAR(100) NOT NULL,
  apellido    VARCHAR(100) NOT NULL,
  telefono    VARCHAR(20),
  email       VARCHAR(100),
  cargo       VARCHAR(50) NOT NULL
);

CREATE TABLE turno_personal (
  id_turno_personal BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_turno     BIGINT NOT NULL REFERENCES turno(id_turno),
  id_personal  BIGINT NOT NULL REFERENCES personal(id_personal),
  fecha_asignacion DATE NOT NULL,
  descripcion  TEXT,
  UNIQUE (id_turno, id_personal, fecha_asignacion)
);

-- ============================ REPORTES ===============================
CREATE TABLE reporte (
  id_reporte       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_usuario       BIGINT NOT NULL REFERENCES usuario(id_usuario),
  tipo             VARCHAR(50) NOT NULL,
  fecha_generacion TIMESTAMP NOT NULL DEFAULT now(),
  formato          VARCHAR(10) NOT NULL CHECK (formato IN ('PDF','XLSX','CSV')),
  ruta_archivo     VARCHAR(255)
);

-- =====================================================================
-- FUNCIONES DE AUDITORÍA
-- El backend debe ejecutar al abrir cada transacción:
--   SET LOCAL app.user_id = '<id_usuario>';  SET LOCAL app.ip = '<ip>';
-- =====================================================================
CREATE OR REPLACE FUNCTION fn_set_campos_auditoria() RETURNS trigger AS $$
DECLARE v_user BIGINT := NULLIF(current_setting('app.user_id', true), '')::BIGINT;
BEGIN
  IF TG_OP = 'INSERT' THEN
    NEW.creado_en := now();  NEW.creado_por := v_user;  NEW.activo := COALESCE(NEW.activo, TRUE);
  ELSE
    NEW.creado_en := OLD.creado_en;  NEW.creado_por := OLD.creado_por;
    NEW.actualizado_en := now();     NEW.actualizado_por := v_user;
    IF NEW.eliminado_en IS NOT NULL AND OLD.eliminado_en IS NULL THEN
      NEW.eliminado_por := v_user;  NEW.activo := FALSE;   -- borrado lógico
    END IF;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION fn_bitacora() RETURNS trigger AS $$
DECLARE
  v_pk   TEXT := TG_ARGV[0];
  v_user BIGINT := NULLIF(current_setting('app.user_id', true), '')::BIGINT;
  v_ip   TEXT := NULLIF(current_setting('app.ip', true), '');
BEGIN
  IF TG_OP = 'INSERT' THEN
    INSERT INTO auditoria (id_usuario, tabla_afectada, accion, registro_id, datos_despues, ip_origen)
    VALUES (v_user, TG_TABLE_NAME, 'INSERT', (to_jsonb(NEW)->>v_pk)::BIGINT, to_jsonb(NEW), v_ip);
    RETURN NEW;
  ELSIF TG_OP = 'UPDATE' THEN
    INSERT INTO auditoria (id_usuario, tabla_afectada, accion, registro_id, datos_antes, datos_despues, ip_origen)
    VALUES (v_user, TG_TABLE_NAME, 'UPDATE', (to_jsonb(NEW)->>v_pk)::BIGINT, to_jsonb(OLD), to_jsonb(NEW), v_ip);
    RETURN NEW;
  ELSE
    INSERT INTO auditoria (id_usuario, tabla_afectada, accion, registro_id, datos_antes, ip_origen)
    VALUES (v_user, TG_TABLE_NAME, 'DELETE', (to_jsonb(OLD)->>v_pk)::BIGINT, to_jsonb(OLD), v_ip);
    RETURN OLD;
  END IF;
END $$ LANGUAGE plpgsql;

-- =====================================================================
-- APLICAR LOS 7 CAMPOS + TRIGGERS A TODAS LAS TABLAS DE NEGOCIO
-- =====================================================================
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'rol','usuario','categoria_producto','producto','receta','categoria_insumo','insumo',
    'receta_detalle','proveedor','compra','detalle_compra','mesa','estado_pedido','pedido',
    'detalle_pedido','notificacion_cocina','movimiento_inventario','metodo_pago','cuenta',
    'detalle_cuenta','pago','comprobante','turno','personal','turno_personal','reporte']
  LOOP
    EXECUTE format($f$
      ALTER TABLE %1$I
        ADD COLUMN creado_en       TIMESTAMP NOT NULL DEFAULT now(),
        ADD COLUMN creado_por      BIGINT REFERENCES usuario(id_usuario),
        ADD COLUMN actualizado_en  TIMESTAMP,
        ADD COLUMN actualizado_por BIGINT REFERENCES usuario(id_usuario),
        ADD COLUMN eliminado_en    TIMESTAMP,
        ADD COLUMN eliminado_por   BIGINT REFERENCES usuario(id_usuario),
        ADD COLUMN activo          BOOLEAN NOT NULL DEFAULT TRUE;
      CREATE TRIGGER trg_%1$s_campos BEFORE INSERT OR UPDATE ON %1$I
        FOR EACH ROW EXECUTE FUNCTION fn_set_campos_auditoria();
      CREATE TRIGGER trg_%1$s_bitacora AFTER INSERT OR UPDATE OR DELETE ON %1$I
        FOR EACH ROW EXECUTE FUNCTION fn_bitacora(%2$L);
    $f$, t, 'id_' || CASE t
        WHEN 'categoria_producto' THEN 'categoria_producto'
        WHEN 'categoria_insumo'   THEN 'categoria_insumo'
        WHEN 'notificacion_cocina' THEN 'notificacion'
        WHEN 'movimiento_inventario' THEN 'movimiento'
        ELSE t END);
  END LOOP;
END $$;

-- =====================================================================
-- ÍNDICES ÚNICOS PARCIALES (borrado lógico)
-- Deben ir DESPUÉS del bloque DO, porque usan la columna eliminado_en.
-- =====================================================================
CREATE UNIQUE INDEX ux_usuario_email ON usuario (lower(email)) WHERE eliminado_en IS NULL;
CREATE UNIQUE INDEX ux_mesa_numero   ON mesa (numero)          WHERE eliminado_en IS NULL;

-- =====================================================================
-- ÍNDICES SOBRE FOREIGN KEYS (rendimiento en JOINs)
-- =====================================================================
CREATE INDEX ix_usuario_rol        ON usuario(id_rol);
CREATE INDEX ix_auditoria_tabla    ON auditoria(tabla_afectada, registro_id);
CREATE INDEX ix_auditoria_usuario  ON auditoria(id_usuario, fecha_hora);
CREATE INDEX ix_producto_categoria ON producto(id_categoria_producto);
CREATE INDEX ix_insumo_categoria   ON insumo(id_categoria_insumo);
CREATE INDEX ix_recdet_insumo      ON receta_detalle(id_insumo);
CREATE INDEX ix_compra_proveedor   ON compra(id_proveedor);
CREATE UNIQUE INDEX ux_compra_solicitud ON compra(solicitud_id);
CREATE INDEX ix_detcompra_compra   ON detalle_compra(id_compra);
CREATE INDEX ix_pedido_mesa        ON pedido(id_mesa);
CREATE INDEX ix_pedido_mozo        ON pedido(id_usuario_mozo);
CREATE INDEX ix_pedido_estado      ON pedido(id_estado_pedido, fecha_hora);
CREATE INDEX ix_detpedido_pedido   ON detalle_pedido(id_pedido);
CREATE INDEX ix_detpedido_producto ON detalle_pedido(id_producto);
CREATE INDEX ix_notif_pedido       ON notificacion_cocina(id_pedido, leida);
CREATE INDEX ix_movinv_insumo      ON movimiento_inventario(id_insumo, fecha_movimiento);
CREATE INDEX ix_cuenta_pedido      ON cuenta(id_pedido);
CREATE INDEX ix_detcuenta_cuenta   ON detalle_cuenta(id_cuenta);
CREATE INDEX ix_pago_cuenta        ON pago(id_cuenta);
CREATE INDEX ix_comprobante_cuenta ON comprobante(id_cuenta);
CREATE INDEX ix_turnopers_personal ON turno_personal(id_personal);
CREATE INDEX ix_reporte_usuario    ON reporte(id_usuario);

-- =====================================================================
-- DATOS INICIALES
-- =====================================================================
INSERT INTO rol (nombre, descripcion) VALUES
 ('Administrador','Gestiona todo el sistema'),('Cajero','Cuentas, pagos y facturación'),
 ('Mozo','Toma pedidos y atiende mesas'),('Cocinero','Prepara platos'),
 ('Jefe de Cocina','Supervisa producción'),('Recepcionista','Cuadre y cierre de caja'),
 ('Cliente','Consume en el local o solicita para llevar');

INSERT INTO estado_pedido (nombre, orden) VALUES
 ('REGISTRADO',1),('EN_PREPARACION',2),('LISTO',3),('SERVIDO',4),('PAGADO',5),('ANULADO',6);

INSERT INTO metodo_pago (nombre) VALUES ('Efectivo'),('Yape'),('Plin'),('Tarjeta');


-- ===================== AJUSTES PARA EL BACKEND ========================
-- Guarda el nombre de quien solicita un pedido para llevar.
ALTER TABLE pedido ADD COLUMN IF NOT EXISTS cliente_nombre VARCHAR(100);
-- Identifica la solicitud original para evitar registrar dos veces el mismo pedido.
ALTER TABLE pedido ADD COLUMN IF NOT EXISTS solicitud_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS ux_pedido_solicitud ON pedido(solicitud_id);
-- Identifica cada solicitud de pago para evitar cobros repetidos.
ALTER TABLE pago ADD COLUMN IF NOT EXISTS solicitud_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS ux_pago_solicitud ON pago(solicitud_id);
-- Permite guardar el RUC del cliente en el comprobante.
ALTER TABLE comprobante ADD COLUMN IF NOT EXISTS cliente_ruc VARCHAR(11);
-- Guarda la huella del contenido de las solicitudes para detectar reintentos diferentes.
ALTER TABLE pedido ADD COLUMN IF NOT EXISTS solicitud_hash VARCHAR(64);
ALTER TABLE pago ADD COLUMN IF NOT EXISTS solicitud_hash VARCHAR(64);
-- Genera números correlativos para los tickets internos.
CREATE SEQUENCE IF NOT EXISTS ticket_correlativo_seq;
-- Reemplaza la función original para excluir password_hash de la bitácora.
CREATE OR REPLACE FUNCTION fn_bitacora() RETURNS trigger AS $$
DECLARE
 v_pk TEXT := TG_ARGV[0];
 v_user BIGINT := NULLIF(current_setting('app.user_id', true), '')::BIGINT;
 v_ip TEXT := NULLIF(current_setting('app.ip', true), '');
 v_antes JSONB; v_despues JSONB;
BEGIN
 IF TG_OP <> 'INSERT' THEN v_antes := to_jsonb(OLD) - 'password_hash'; END IF;
 IF TG_OP <> 'DELETE' THEN v_despues := to_jsonb(NEW) - 'password_hash'; END IF;
 INSERT INTO auditoria(id_usuario,tabla_afectada,accion,registro_id,datos_antes,datos_despues,ip_origen)
 VALUES(v_user,TG_TABLE_NAME,TG_OP,(COALESCE(v_despues,v_antes)->>v_pk)::BIGINT,v_antes,v_despues,v_ip);
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$ LANGUAGE plpgsql;
-- Elimina también las contraseñas cifradas de registros de auditoría anteriores.
UPDATE auditoria SET datos_antes=datos_antes-'password_hash',datos_despues=datos_despues-'password_hash' WHERE tabla_afectada='usuario';

-- Registra cada envío de productos, incluidos los añadidos al pedido activo de una mesa.
-- Su clave única impide repetir el mismo envío si se reintenta la solicitud.
CREATE TABLE IF NOT EXISTS solicitud_pedido (
 solicitud_id UUID PRIMARY KEY,
 id_pedido BIGINT NOT NULL REFERENCES pedido(id_pedido),
 id_usuario BIGINT NOT NULL REFERENCES usuario(id_usuario),
 solicitud_hash VARCHAR(64) NOT NULL,
 fecha TIMESTAMP NOT NULL DEFAULT now()
);
-- Recupera solicitudes anteriores que ya tengan identificador y huella.
INSERT INTO solicitud_pedido(solicitud_id,id_pedido,id_usuario,solicitud_hash)
SELECT solicitud_id,id_pedido,id_usuario_mozo,solicitud_hash FROM pedido
WHERE solicitud_id IS NOT NULL AND solicitud_hash IS NOT NULL
ON CONFLICT(solicitud_id) DO NOTHING;

-- Turnos disponibles para la asignación de personal.
INSERT INTO turno(nombre,hora_inicio,hora_fin) VALUES
 ('Mañana','06:00','14:00'),
 ('Tarde','14:00','22:00'),
 ('Noche','22:00','06:00');

-- ===================== DATOS DE DEMOSTRACIÓN =========================
-- Evita cargar los datos de demostración sobre usuarios, productos o mesas existentes.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM usuario) OR EXISTS(SELECT 1 FROM producto) OR EXISTS(SELECT 1 FROM mesa) THEN
  RAISE EXCEPTION 'Cargue estos datos solo en una base vacía de usuarios, productos y mesas.';
 END IF;
END $$;
-- Crea cuatro accesos de demostración: admin, mozo, cocina y caja, con dominio @demo.local.
-- Contraseña inicial de todos: Cambiar123!; se almacena cifrada con bcrypt.
-- Cambiar estas contraseñas antes de utilizar datos reales.
INSERT INTO usuario(id_rol,nombre,apellido,email,password_hash)
SELECT id_rol,nombre,'Demo',lower(CASE nombre WHEN 'Administrador' THEN 'admin' WHEN 'Cocinero' THEN 'cocina' WHEN 'Cajero' THEN 'caja' ELSE 'mozo' END)||'@demo.local',crypt('Cambiar123!',gen_salt('bf',10))
FROM rol WHERE nombre IN ('Administrador','Mozo','Cocinero','Cajero');
-- Crea nueve mesas de cuatro personas, inicialmente libres.
INSERT INTO mesa(numero,capacidad,ubicacion) SELECT n,4,'Salón' FROM generate_series(1,9) n;
-- Agrupa los productos de la carta por tipo.
INSERT INTO categoria_producto(nombre) VALUES('Plato Típico'),('Mariscos'),('Bebida');
-- Agrupa las existencias de demostración por porciones.
INSERT INTO categoria_insumo(nombre) VALUES('Porciones');
-- Carga la carta de demostración y sus precios en soles.
INSERT INTO producto(id_categoria_producto,nombre,precio_venta) VALUES
((SELECT id_categoria_producto FROM categoria_producto WHERE nombre='Plato Típico'),'Carapulcra con Sopa Seca',32),
((SELECT id_categoria_producto FROM categoria_producto WHERE nombre='Mariscos'),'Ceviche Mixto Iqueño',38),
((SELECT id_categoria_producto FROM categoria_producto WHERE nombre='Plato Típico'),'Seco de Ica con Pallares',34),
((SELECT id_categoria_producto FROM categoria_producto WHERE nombre='Plato Típico'),'Tacu tacu con lomo',24),
((SELECT id_categoria_producto FROM categoria_producto WHERE nombre='Mariscos'),'Chicharrón de pota',18),
((SELECT id_categoria_producto FROM categoria_producto WHERE nombre='Bebida'),'Pisco Sour Iqueño',18),
((SELECT id_categoria_producto FROM categoria_producto WHERE nombre='Bebida'),'Jarra de Chicha Morada',15),
((SELECT id_categoria_producto FROM categoria_producto WHERE nombre='Bebida'),'Inka Kola 1 Litro',10);
-- Asigna cien porciones disponibles y un mínimo de diez por cada producto.
INSERT INTO insumo(id_categoria_insumo,nombre,unidad_medida,stock_actual,stock_minimo)
SELECT (SELECT id_categoria_insumo FROM categoria_insumo WHERE nombre='Porciones'),'Porción de '||nombre,'unidad',100,10 FROM producto;
-- Define recetas sencillas que consumen una porción por producto vendido.
INSERT INTO receta(id_producto,descripcion) SELECT id_producto,'Consumo de una porción disponible' FROM producto;
-- Relaciona cada receta con su porción; las unidades coinciden con el inventario.
INSERT INTO receta_detalle(id_receta,id_insumo,cantidad,unidad_medida)
SELECT r.id_receta,i.id_insumo,1,'unidad' FROM receta r JOIN producto p USING(id_producto) JOIN insumo i ON i.nombre='Porción de '||p.nombre;

-- Confirma el esquema, los ajustes y los datos en una sola transacción.
COMMIT;
