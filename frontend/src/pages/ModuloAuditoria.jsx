import Panel from '../components/Panel.jsx';
import Tabla from '../components/Tabla.jsx';
import EncabezadoModulo from '../components/EncabezadoModulo.jsx';
import { Indicador, RejillaIndicadores } from '../components/Indicador.jsx';
import { Advertencias, Cargando, Diagnostico, ErrorConsulta } from '../components/Estado.jsx';
import { claseMarca, entero, fechaHora, texto } from '../components/Formato.js';
import { useRecurso } from '../hooks/useRecurso.js';

/** Modulo 5 - Auditoria. */
export default function ModuloAuditoria({ controles }) {
  const { datos, cargando, error, actualizado, recargar } = useRecurso('/auditoria/resumen');

  const sysadmins = (datos?.logins ?? []).filter((login) => login.esSysadmin).length;
  const deshabilitados = (datos?.logins ?? []).filter((login) => login.deshabilitado).length;

  return (
    <>
      <EncabezadoModulo
        titulo="Auditoria"
        descripcion="Usuarios registrados, roles, privilegios asignados y objetos con dependencias o errores de compilacion."
        actualizado={actualizado}
        alRecargar={recargar}
      >
        {controles}
      </EncabezadoModulo>

      {error && <ErrorConsulta error={error} alReintentar={recargar} />}
      {cargando && !datos && <Cargando />}

      {datos && (
        <>
          <Diagnostico
            texto={datos.diagnosticoSeguridad}
            nivel={datos.diagnosticoSeguridad?.startsWith('Puntos de atencion') ? 'alerta' : 'informacion'}
          />
          <Advertencias lista={datos.advertencias} />

          <RejillaIndicadores>
            <Indicador etiqueta="Logins de instancia" valor={entero(datos.logins?.length)} />
            <Indicador
              etiqueta="Miembros de sysadmin"
              valor={entero(sysadmins)}
              nivel={sysadmins > 2 ? 'ADVERTENCIA' : 'NORMAL'}
            />
            <Indicador etiqueta="Logins deshabilitados" valor={entero(deshabilitados)} />
            <Indicador etiqueta="Usuarios de la base" valor={entero(datos.usuarios?.length)} />
            <Indicador etiqueta="Asignaciones de rol" valor={entero(datos.roles?.length)} />
            <Indicador
              etiqueta="Objetos con problemas"
              valor={entero(datos.objetosConProblemas?.length)}
              nivel={datos.objetosConProblemas?.length > 0 ? 'ADVERTENCIA' : 'NORMAL'}
            />
          </RejillaIndicadores>

          <Panel titulo="Logins registrados" fuente="sys.server_principals" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'login', encabezado: 'Login' },
                { clave: 'tipo', encabezado: 'Tipo' },
                {
                  clave: 'deshabilitado',
                  encabezado: 'Estado',
                  presentar: (f) => (
                    <span className={claseMarca(f.deshabilitado ? 'MEDIO' : 'NORMAL')}>
                      {f.deshabilitado ? 'Deshabilitado' : 'Habilitado'}
                    </span>
                  )
                },
                {
                  clave: 'esSysadmin',
                  encabezado: 'sysadmin',
                  presentar: (f) => (f.esSysadmin ? <span className={claseMarca('MEDIO')}>Si</span> : 'No')
                },
                { clave: 'baseDatosPredeterminada', encabezado: 'BD predeterminada', presentar: (f) => texto(f.baseDatosPredeterminada) },
                { clave: 'fechaCreacion', encabezado: 'Creado', presentar: (f) => fechaHora(f.fechaCreacion) }
              ]}
              filas={datos.logins}
              claveFila={(fila) => fila.login}
            />
          </Panel>

          <Panel titulo="Usuarios de la base de datos" fuente="sys.database_principals" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'usuario', encabezado: 'Usuario' },
                { clave: 'tipo', encabezado: 'Tipo' },
                { clave: 'loginAsociado', encabezado: 'Login asociado', presentar: (f) => texto(f.loginAsociado) },
                { clave: 'esquemaPredeterminado', encabezado: 'Esquema', presentar: (f) => texto(f.esquemaPredeterminado) },
                { clave: 'tipoAutenticacion', encabezado: 'Autenticacion', presentar: (f) => texto(f.tipoAutenticacion) },
                { clave: 'fechaCreacion', encabezado: 'Creado', presentar: (f) => fechaHora(f.fechaCreacion) }
              ]}
              filas={datos.usuarios}
              claveFila={(fila) => fila.usuario}
            />
          </Panel>

          <Panel titulo="Roles y miembros" fuente="sys.database_role_members · sys.server_role_members" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'ambito', encabezado: 'Ambito' },
                { clave: 'rol', encabezado: 'Rol' },
                { clave: 'miembro', encabezado: 'Miembro' },
                { clave: 'tipoMiembro', encabezado: 'Tipo' }
              ]}
              filas={datos.roles}
              claveFila={(fila, indice) => indice}
            />
          </Panel>

          <Panel titulo="Privilegios asignados" fuente="sys.database_permissions" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'beneficiario', encabezado: 'Beneficiario' },
                { clave: 'tipoBeneficiario', encabezado: 'Tipo' },
                {
                  clave: 'estado',
                  encabezado: 'Estado',
                  presentar: (f) => (
                    <span className={claseMarca(f.estado === 'DENY' ? 'MEDIO' : 'NORMAL')}>{f.estado}</span>
                  )
                },
                { clave: 'privilegio', encabezado: 'Privilegio' },
                { clave: 'clase', encabezado: 'Clase' },
                { clave: 'objeto', encabezado: 'Objeto', presentar: (f) => texto(f.objeto) }
              ]}
              filas={datos.privilegios}
              claveFila={(fila, indice) => indice}
            />
          </Panel>

          <Panel titulo="Objetos con dependencias rotas" fuente="sys.sql_expression_dependencies" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'esquema', encabezado: 'Esquema' },
                { clave: 'objeto', encabezado: 'Objeto' },
                { clave: 'tipoObjeto', encabezado: 'Tipo' },
                { clave: 'entidadReferenciada', encabezado: 'Entidad referenciada' },
                { clave: 'diagnostico', encabezado: 'Diagnostico' }
              ]}
              filas={datos.objetosConProblemas}
              claveFila={(fila, indice) => indice}
              mensajeVacio="No se detectaron dependencias rotas entre los objetos de la base de datos."
            />
          </Panel>
        </>
      )}
    </>
  );
}
