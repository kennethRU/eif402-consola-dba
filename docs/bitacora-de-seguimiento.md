# Bitácora de seguimiento del proyecto

**Proyecto:** Consola DBA para SQL Server · EIF402 · Grupo 2
**Repositorio:** `github.com/kennethRU/eif402-consola-dba`
**Período:** 13 de septiembre al 3 de noviembre de 2026

Las fechas, autores y mensajes provienen del historial de Git (`git log`). Cada integrante
subió su trabajo desde su propia cuenta.

---

## 1. Responsables

| Integrante | Cuenta en GitHub | Responsabilidad |
|---|---|---|
| Kenneth Ramírez Ugalde | Kenneth Ramírez | Estructura base, conexiones, componentes compartidos, coordinación del repositorio |
| Laura Fonseca Brenes | lif964 | Base `QuinielasDB`, esquema `dba`, procedimientos, permisos, datos de prueba |
| Brenda Serrano Jiménez | Brenda Serrano J. | M1 Instancia y M2 Rendimiento |
| Ahymara Lucila Ureña Chanto | Ahyma | M3 Almacenamiento y M4 Respaldos |
| Mariana Villalobos Ramírez | MarianaVillalobos04 | M5 Auditoría, M6 Mantenimiento y ensamblaje (`App.jsx`) |

---

## 2. Registro de avances

| Fecha | Integrante | Actividad | Commit |
|---|---|---|---|
| 13 sep 2026 | Kenneth Ramírez | Estructura base del proyecto: backend Spring Boot, frontend React + Vite y documentación inicial | `4891d64` |
| 13 sep 2026 | Kenneth Ramírez | `.gitignore` para excluir dependencias y credenciales locales | `e5beea4` |
| 13 sep 2026 | Kenneth Ramírez | Configuración para evitar que VS Code cambie la versión de Java del `pom.xml` | `62e81a4` |
| 27 sep 2026 | Laura Fonseca | Creación de `QuinielasDB` y tablas del esquema `quiniela` | `2aea249` |
| 27 sep 2026 | Laura Fonseca | Vistas, procedimientos y trigger de `QuinielasDB` | `21a3b88` |
| 27 sep 2026 | Laura Fonseca | Datos de ejemplo y funciones de `QuinielasDB` | `65d11db` |
| 28 sep 2026 | Laura Fonseca | Esquema `dba` y procedimientos de mantenimiento | `b9780aa` |
| 28 sep 2026 | Laura Fonseca | Login `dba_monitor` con privilegios mínimos | `7ac0e36` |
| 28 sep 2026 | Laura Fonseca | Catálogo documentado de consultas administrativas | `999bda4` |
| 29 sep 2026 | Laura Fonseca | Carga de datos de prueba y preparación de la demostración | `ec8dd75` |
| 29 sep 2026 | Laura Fonseca | Corrección del orden de scripts y del recálculo de puntajes con el trigger activo | `545b34c` |
| 2 oct 2026 | Brenda Serrano | M1: estado general de la instancia con `SERVERPROPERTY` y `dm_os_sys_info` | `b62fa65` |
| 2 oct 2026 | Brenda Serrano | M2: consultas costosas, sesiones activas y detección de bloqueos | `422e86a` |
| 21 oct 2026 | Ahymara Ureña | M3: espacio por filegroup, objetos voluminosos y crecimiento histórico | `d2bc706` |
| 21 oct 2026 | Ahymara Ureña | M4: historial de respaldos y evaluación de riesgo por base | `9d6647a` |
| 22 oct 2026 | Mariana Villalobos | M5: auditoría de usuarios, roles, privilegios y dependencias rotas | `c9b65f6` |
| 23 oct 2026 | Mariana Villalobos | M6: estadísticas, fragmentación, validación y recompilación de objetos | `afb74a5` |
| 24 oct 2026 | Mariana Villalobos | Ensamblaje de la consola: navegación entre los seis módulos | `76792a1` |
| 25 oct 2026 | Kenneth Ramírez | Corrección del mensaje de jobs de respaldo vacíos en M4 | `6522b5c` |
| 25 oct 2026 | Equipo | Prueba integral desde el repositorio clonado: compila 35 archivos y los seis módulos muestran datos reales | — |
| 25 oct 2026 | Kenneth Ramírez | Manual de usuario, bitácora, evidencias de pruebas y presentación agregados a `docs/`; README corregido | `a6fd163` a `2df5f51` |
| 25–26 oct 2026 | Kenneth Ramírez | Ejecución de las pruebas pendientes: Bloque C (dependencia rota, validación y recompilación), bloqueo entre sesiones, ordenamiento, capturas de almacenamiento, recálculo de estadísticas y validación de identificadores. 17 pruebas funcionales aprobadas, 6 observaciones registradas | — |
| 26 oct 2026 | Kenneth Ramírez | Corrección de dos observaciones: fechas del servidor mostradas en UTC (O-01) y distinción entre sysadmin directo y por grupo (O-02). Verificadas en P-19 y P-20 | — |

---

## 3. Problemas encontrados y decisiones

| Fecha | Problema o decisión | Resolución |
|---|---|---|
| 13 sep | El editor reescribía `<java.version>` y rompía el build | Configuración de VS Code en el repositorio; la versión queda fija en 17 |
| 29 sep | El trigger `INSTEAD OF UPDATE` de `quiniela.Pronostico` impedía la carga y el mantenimiento | Desactivación del trigger durante la carga de datos; corrección del orden de scripts |
| Integración | Conflicto de intercalaciones (error 451) en la consulta de roles de M5 | `COLLATE DATABASE_DEFAULT` |
| Integración | `smallint` recibido como `Short` tumbaba M2 | `rs.getInt()` con chequeo de nulo |
| Integración | `sys.sql_expression_dependencies` no hereda `VIEW DEFINITION` | `GRANT SELECT` explícito para `dba_monitor` |
| Integración | Pseudo-tablas `inserted`/`deleted` reportadas como dependencias rotas | Exclusión explícita en la consulta |
| 25 oct | El repositorio es público: ¿riesgo de exponer credenciales? | Se mantiene público; se verificó que solo hay marcadores y que la contraseña real no está en el historial |

---

## 4. Cierre del proyecto

| Fecha | Integrante | Actividad | Estado |
|---|---|---|---|
| 25 oct 2026 | Kenneth Ramírez | Subida de la presentación, manual de usuario, evidencias y bitácora a `docs/` | Listo |
| 25 oct 2026 | Kenneth Ramírez | Ensayo de la demostración de objetos inválidos (M5 y M6) | Listo |
| _Por completar_ | Equipo | Ensayo completo de la presentación (15 minutos) | Pendiente |
| 3 nov 2026 | Equipo | Presentación y defensa en vivo | Pendiente |
