# Verificación de la integración v2

- 19 pruebas unitarias aprobadas con Maven, sin fallos ni errores.
- Flujo HTTP verificado contra PostgreSQL 16 en una base temporal: login por rol, CSRF, permisos, pedidos, productos adicionales, stock, cocina, cobro, reintentos, anulación, concurrencia y Excel.
- Gestión verificada: compras con varios insumos, reintentos sin duplicar, reversión de una compra inválida, entrada, merma, ajuste, usuarios sin exposición de hashes, revocación de sesiones al desactivar, personal, asignación de turnos, precios, recetas y reportes.
- Comprobación de instalación desde cero con schema.sql y repetición de actualizar_v2.sql sin pérdida de datos.
- Interfaz comprobada en un DOM de prueba con peticiones reales: login, pedido, cocina, reparto, cobro, navegación de los seis módulos y cierre de sesión.
- JavaScript revisado con node --check y referencias entre HTML y acciones verificadas.

Para repetir: `mvn test` desde backend. Con servidor y base exclusiva de prueba: `python tests/smoke_api.py` y `python tests/gestion_api.py`. Las pruebas HTTP crean y modifican registros.

No se ejecutaron pruebas en Windows ni una revisión visual en un navegador completo. El archivo iniciar.bat se revisó como script; debe ejecutarse en Windows con Java y Maven en el PATH.
