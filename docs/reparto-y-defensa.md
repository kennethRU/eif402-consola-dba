# Reparto del trabajo y preparación de la defensa

Este documento no es entregable del proyecto: es una guía interna del equipo.

---

## 1. Por qué importa el reparto

La rúbrica asigna **8 puntos al historial de commits**: exige participación equilibrada,
commits progresivos y mensajes descriptivos. Un repositorio con tres commits gigantes el día
antes de la entrega pierde esos puntos aunque el código sea perfecto.

Además, la evaluación incluye **defensa oral individual**. Cada integrante debe poder explicar
por qué su módulo usa una vista y no otra, qué significa cada métrica y qué haría un DBA con
esa información.

---

## 2. Reparto sugerido

La estructura del proyecto ya está separada por módulo justamente para esto: dos personas
pueden trabajar en paralelo sin tocar los mismos archivos.

| Integrante | Backend | Frontend | Scripts SQL |
|---|---|---|---|
| 1 | `conexion/`, `config/`, `common/` | `App.jsx`, `BarraInstrumentos`, `SelectorConexion` | `04_permisos_monitoreo.sql` |
| 2 | `instancia/` (M1) | `ModuloInstancia.jsx` | Bloque M1 de `03_consultas` |
| 3 | `rendimiento/` (M2) | `ModuloRendimiento.jsx` | Bloque M2 de `03_consultas` |
| 4 | `almacenamiento/` (M3) + `respaldo/` (M4) | `ModuloAlmacenamiento`, `ModuloRespaldos` | `01_esquema_dba.sql`, bloques M3/M4 |
| 5 | `auditoria/` (M5) + `mantenimiento/` (M6) | `ModuloAuditoria`, `ModuloMantenimiento` | `02_procedimientos.sql`, bloques M5/M6 |

Si el equipo es de cuatro, la persona 1 asume además uno de los módulos ligeros.

**Responsabilidades compartidas:** README, documento de instalación, video de demostración,
carga de datos de prueba.

---

## 3. Convención de commits

Un commit por unidad de trabajo con sentido, en presente y en español:

```
M2: agregar consulta de sesiones bloqueadas con dm_exec_requests
M2: mostrar tipo de espera y recurso bloqueado en la tabla
M2: interpretar bloqueos mayores a 5 segundos como críticos
M3: registrar snapshot de tamaño desde el procedimiento almacenado
docs: documentar por qué backupset no registra respaldos fallidos
fix: corregir tipo del parámetro nulo en el historial de respaldos
```

Evite `cambios`, `update`, `avance`, `arreglos varios`.

Trabajen en ramas por módulo (`modulo-2-rendimiento`) e integren con pull request. El
historial de merges evidencia colaboración real.

---

## 4. Datos de prueba

Las métricas no se ven en una base vacía. Antes de la demostración:

1. Cargue al menos unos cientos de miles de filas en dos o tres tablas.
2. Ejecute consultas pesadas varias veces para poblar el caché de planes (M2).
3. Abra dos sesiones en SSMS: en una, `BEGIN TRAN` + `UPDATE` sin confirmar; en otra, un
   `SELECT` sobre la misma fila. Eso genera un bloqueo visible en M2.
4. Ejecute un `BACKUP DATABASE` completo y uno diferencial (M4).
5. Ejecute «Registrar captura» en M3 varios días distintos, o inserte filas en
   `dba.snapshot_almacenamiento` con fechas anteriores para tener una serie.
6. Para demostrar M6: cree una vista sobre una tabla, renombre la tabla y ejecute
   «Validar objetos». La vista aparecerá como objeto inválido; luego restaure el nombre y
   pruebe «Recompilar pendientes».

---

## 5. Preguntas probables en la defensa

**M1 — ¿Cómo sabe la herramienta que la instancia está en línea?**
No existe una columna «estado de la instancia»: si `sys.dm_os_sys_info` responde, la instancia
está atendiendo peticiones. Complementariamente se consulta `sys.dm_server_services` para el
estado del servicio en el sistema operativo.

**M2 — ¿Por qué `total_worker_time` y no `total_elapsed_time`?**
`total_worker_time` es tiempo de CPU real; `total_elapsed_time` incluye esperas por bloqueo o
E/S. Una consulta lenta por bloqueo no es una consulta costosa en CPU. La consola permite
ordenar por ambos criterios precisamente para distinguirlos.

**M2 — ¿Por qué los datos del caché de planes no son históricos?**
Se acumulan desde `creation_time` del plan y se pierden al reiniciar la instancia o al limpiar
el caché. Para histórico real se usaría Query Store.

**M3 — ¿Por qué `FILEPROPERTY(name,'SpaceUsed')` y no `sp_spaceused`?**
`sp_spaceused` devuelve varios conjuntos de resultados y es incómodo de consumir desde JDBC.
`FILEPROPERTY` da el espacio usado por archivo en páginas, que se convierte a MB multiplicando
por 8 y dividiendo entre 1024.

**M4 — ¿Cómo detecta un respaldo fallido?**
No desde `backupset`, que solo guarda los exitosos, sino desde `msdb.dbo.sysjobhistory` con
`run_status = 0`.

**M5 — ¿Qué es un objeto inválido en SQL Server?**
Estrictamente no existe la categoría. Se aproxima con dependencias rotas
(`sys.sql_expression_dependencies` con `referenced_id IS NULL`) y con el intento de refrescar
cada módulo vía `sp_refreshsqlmodule`.

**M6 — ¿Qué hace realmente «recompilar»?**
`sp_refreshsqlmodule` actualiza los metadatos de un módulo no esquematizado sin cambiar su
definición, permisos ni propiedades extendidas. Es el equivalente práctico de
`ALTER ... COMPILE` de Oracle.

**Transversal — ¿Por qué el backend no arma SQL con concatenación?**
Para evitar inyección. Los valores van como parámetros; lo que no puede parametrizarse (la
columna de `ORDER BY`) se valida contra una lista blanca en `RepositorioAdministrativo`.

---

## 6. Lista de verificación antes de entregar

- [ ] El repositorio no contiene contraseñas ni cadenas de conexión reales
- [ ] `application.yml` solo referencia variables de entorno
- [ ] Cada integrante tiene commits distribuidos en el tiempo, no en un solo día
- [ ] Los seis módulos muestran datos reales, no tablas vacías
- [ ] El manejo de errores se demuestra: apagar el servicio de SQL Server y mostrar que la
      consola informa el problema en lugar de romperse
- [ ] El video muestra la ejecución de una acción de mantenimiento y su confirmación
- [ ] Todos pueden explicar cualquier módulo, no solo el propio
