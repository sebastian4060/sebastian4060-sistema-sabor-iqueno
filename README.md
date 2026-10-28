# El Sabor Iqueño

Sistema con Java 17, Spring Boot, PostgreSQL y frontend HTML/CSS/JavaScript. La interfaz v2 está conectada al backend; los cambios se guardan en la base de datos.

## 1. Base de datos

- **Si ya tienes la base del proyecto anterior:** ejecuta únicamente `database/actualizar_v2.sql` en pgAdmin sobre esa base. Conserva tus datos.
- **Si comienzas desde cero:** crea `sabor_iqueno` y ejecuta `database/schema.sql`. Incluye los datos demo y los cambios v2. No ejecutes el esquema sobre una base que ya tenga tablas.

## 2. Configuración

Copia `backend/config/application-local.properties.example` a `backend/config/application-local.properties` y coloca tus datos:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/sabor_iqueno
spring.datasource.username=postgres
spring.datasource.password=TU_CLAVE
```

Se necesitan JDK 17 o 21, Maven 3.9 y PostgreSQL 14 o posterior. Java y Maven deben estar en el PATH. No necesitas Node ni instalar JDBC a mano.

## 3. Iniciar

Haz doble clic en `iniciar.bat`, espera el mensaje `Started SaborIquenoApplication` y abre:

**http://localhost:8080/login.html**

También puedes hacerlo desde una terminal:

```powershell
cd backend
mvn clean verify
mvn spring-boot:run
```

Mantén la terminal abierta. Abre las pantallas desde el puerto 8080, sin Live Server. Al editar HTML, CSS o JS, reinicia Spring Boot y recarga con Ctrl+F5.

## 4. Acceso de demostración

| Correo | Rol |
|---|---|
| admin@demo.local | Administrador |
| mozo@demo.local | Mozo |
| cocina@demo.local | Cocinero |
| caja@demo.local | Cajero |

Contraseña inicial: **Cambiar123!**. En una base anterior se conservan las cuentas y contraseñas que ya hayas configurado. Los botones del login solo rellenan los accesos demo.


No te olvides de cambiar las contraseñas!! en PostgreSQL:

UPDATE usuario
SET password_hash = crypt('sebastian', gen_salt('bf', 10))
WHERE email = 'caja@demo.local';


## 5. Funciones

- Mesero: mesas, pedidos para llevar, notas, productos adicionales y anulación antes de preparar.
- Cocina: pedidos por estado, tiempos de espera e impresión de comandas activas.
- Caja: cobro completo por efectivo, tarjeta, Yape o Plin; vuelto y ticket imprimible.
- Inventario: entradas, mermas, ajuste al stock físico, alertas y Excel.
- Compras: registro de proveedores y recepción de varios insumos en una sola transacción.
- Kardex: consulta de movimientos por insumo y comprobación del saldo contra el último movimiento.
- Reportes: ventas acumuladas y de hoy, ventas por fecha, métodos de pago, productos más vendidos y CSV.
- Administración: crear usuarios, activar/desactivar cuentas, registrar personal, asignar el turno de hoy, consultar auditoría, cambiar precios y editar recetas.

El reparto entre comensales es una **calculadora**: distribuye el importe en céntimos; el pago se registra completo en una sola operación. El ticket es **interno y sin validez tributaria**. No hay conexión con SUNAT ni procesamiento bancario. Yape, Plin y tarjeta se confirman externamente antes de registrar el cobro.

Los pedidos se actualizan cada cinco segundos. Las pantallas de gestión se actualizan al entrar, cambiar de pestaña o guardar; vuelve a entrar al módulo para consultar cambios de otro administrador. El kardex muestra los últimos 500 movimientos, compras las últimas 100, auditoría los últimos 200 cambios y reportes las últimas 30 fechas con ventas.

## 6. Organización

- `frontend/index.html`: interfaz del HTML v2.
- `frontend/css/app.css`: estilos.
- `frontend/js/app.js`: conexión HTTP y acciones de las pantallas.
- `backend/src/main/java`: controladores, servicios, DAO, validaciones y seguridad.
- `database/`: esquema y actualización v2.

Se usa BCrypt, permisos por rol, CSRF, consultas parametrizadas y transacciones. Desactivar un usuario también revoca sus sesiones existentes. El navegador no decide el total real del pedido ni el stock.

## 7. Verificación

Desde `backend`:

```powershell
mvn test
mvn package
```

Con servidor y base **exclusiva de prueba**, puedes ejecutar:

```powershell
python tests/smoke_api.py
python tests/gestion_api.py
```

Estas pruebas crean pedidos, ventas, compras y cuentas de prueba. No las ejecutes sobre datos que quieras conservar sin cambios.
