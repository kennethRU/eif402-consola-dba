import Panel from '../components/Panel.jsx';
import Tabla from '../components/Tabla.jsx';
import EncabezadoModulo from '../components/EncabezadoModulo.jsx';
import { Indicador, RejillaIndicadores } from '../components/Indicador.jsx';
import { Advertencias, Cargando, ErrorConsulta } from '../components/Estado.jsx';
import { claseMarca, fechaHora, megabytes, numero, texto } from '../components/Formato.js';

/**
 * Modulo 1 - Estado general de la instancia del SGBD.
 * El recurso lo administra App, porque la barra de instrumentos consume
 * las mismas lecturas y no tiene sentido consultarlas dos veces.
 */
export default function ModuloInstancia({ recurso, controles }) {
  const { datos, cargando, error, actualizado, recargar } = recurso;
  const instancia = datos?.instancia;

  return (
    <>
      <EncabezadoModulo
        titulo="Estado general de la instancia"
        descripcion="Identificacion del servidor, version, disponibilidad y uso de memoria de la instancia monitoreada."
        actualizado={actualizado}
        alRecargar={recargar}
      >
        {controles}
      </EncabezadoModulo>

      {error && <ErrorConsulta error={error} alReintentar={recargar} />}
      {cargando && !datos && <Cargando />}

      {instancia && (
        <>
          <Advertencias lista={datos.advertencias} />

          <RejillaIndicadores>
            <Indicador etiqueta="Servidor" valor={texto(instancia.nombreServidor)} />
            <Indicador
              etiqueta="Instancia"
              valor={texto(instancia.nombreInstancia)}
              nota={texto(instancia.nombreCompletoInstancia)}
            />
            <Indicador etiqueta="Estado" valor={instancia.estado} nivel="NORMAL" />
            <Indicador
              etiqueta="Version"
              valor={texto(instancia.versionProducto)}
              nota={texto(instancia.nivelProducto)}
            />
            <Indicador etiqueta="Inicio" valor={fechaHora(instancia.fechaInicio)} />
            <Indicador etiqueta="Tiempo de actividad" valor={texto(instancia.tiempoActividadLegible)} />
            <Indicador
              etiqueta="Memoria utilizada"
              valor={megabytes(instancia.memoriaUtilizadaMb)}
              nota={`Objetivo: ${megabytes(instancia.memoriaObjetivoMb)}`}
            />
            <Indicador
              etiqueta="CPU logicas"
              valor={numero(instancia.cantidadCpu)}
              nota={`Memoria fisica: ${megabytes(instancia.memoriaFisicaMb)}`}
            />
          </RejillaIndicadores>

          <Panel titulo="Identificacion del gestor" fuente="SERVERPROPERTY · @@VERSION">
            <dl style={{ margin: 0, display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '6px 18px' }}>
              <dt style={{ color: 'var(--acero)' }}>Edicion</dt>
              <dd style={{ margin: 0 }}>{texto(instancia.edicion)}</dd>
              <dt style={{ color: 'var(--acero)' }}>Descripcion</dt>
              <dd style={{ margin: 0 }}>{texto(instancia.descripcionVersion)}</dd>
              <dt style={{ color: 'var(--acero)' }}>Intercalacion</dt>
              <dd style={{ margin: 0 }}>{texto(instancia.intercalacion)}</dd>
              <dt style={{ color: 'var(--acero)' }}>Servicio</dt>
              <dd style={{ margin: 0 }}>{texto(instancia.nombreServicio)}</dd>
            </dl>
          </Panel>

          <Panel titulo="Servicios de la instancia" fuente="sys.dm_server_services" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'nombreServicio', encabezado: 'Servicio' },
                {
                  clave: 'estado',
                  encabezado: 'Estado',
                  presentar: (fila) => (
                    <span className={claseMarca(fila.estado === 'Running' ? 'NORMAL' : 'ADVERTENCIA')}>
                      {fila.estado}
                    </span>
                  )
                },
                { clave: 'tipoInicio', encabezado: 'Tipo de inicio' },
                { clave: 'idProceso', encabezado: 'PID', tipo: 'numero' },
                {
                  clave: 'ultimoArranque',
                  encabezado: 'Ultimo arranque',
                  presentar: (fila) => fechaHora(fila.ultimoArranque)
                },
                { clave: 'cuentaServicio', encabezado: 'Cuenta' }
              ]}
              filas={datos.servicios}
              claveFila={(fila) => fila.nombreServicio}
              mensajeVacio="sys.dm_server_services no esta disponible en esta edicion o falta el permiso VIEW SERVER STATE."
            />
          </Panel>

          <Panel titulo="Bases de datos administradas" fuente="sys.databases · sys.master_files" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'nombre', encabezado: 'Base de datos' },
                {
                  clave: 'estado',
                  encabezado: 'Estado',
                  presentar: (fila) => (
                    <span className={claseMarca(fila.estado === 'ONLINE' ? 'NORMAL' : 'ADVERTENCIA')}>
                      {fila.estado}
                    </span>
                  )
                },
                { clave: 'modeloRecuperacion', encabezado: 'Recuperacion' },
                { clave: 'nivelCompatibilidad', encabezado: 'Compat.', tipo: 'numero' },
                {
                  clave: 'tamanoTotalMb',
                  encabezado: 'Tamano',
                  tipo: 'numero',
                  presentar: (fila) => megabytes(fila.tamanoTotalMb)
                },
                {
                  clave: 'fechaCreacion',
                  encabezado: 'Creada',
                  presentar: (fila) => fechaHora(fila.fechaCreacion)
                }
              ]}
              filas={datos.basesDatos}
              claveFila={(fila) => fila.idBaseDatos}
            />
          </Panel>

          <Panel titulo="Distribucion de memoria" fuente="sys.dm_os_memory_clerks" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'componente', encabezado: 'Componente' },
                {
                  clave: 'memoriaMb',
                  encabezado: 'Memoria',
                  tipo: 'numero',
                  presentar: (fila) => megabytes(fila.memoriaMb)
                }
              ]}
              filas={datos.distribucionMemoria}
              claveFila={(fila) => fila.componente}
            />
          </Panel>
        </>
      )}
    </>
  );
}
