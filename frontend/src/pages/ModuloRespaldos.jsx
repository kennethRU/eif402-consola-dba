import Panel from '../components/Panel.jsx';
import Tabla from '../components/Tabla.jsx';
import EncabezadoModulo from '../components/EncabezadoModulo.jsx';
import { Indicador, RejillaIndicadores } from '../components/Indicador.jsx';
import { Advertencias, Cargando, Diagnostico, ErrorConsulta } from '../components/Estado.jsx';
import { claseMarca, entero, fechaHora, megabytes, texto } from '../components/Formato.js';
import { useRecurso } from '../hooks/useRecurso.js';

/** Modulo 4 - Respaldo y recuperacion. */
export default function ModuloRespaldos({ controles }) {
  const { datos, cargando, error, actualizado, recargar } = useRecurso('/respaldos/resumen');
  const actual = datos?.respaldoBaseActual;

  return (
    <>
      <EncabezadoModulo
        titulo="Respaldo y recuperacion"
        descripcion="Estado de las copias de seguridad de la instancia: ultimo respaldo exitoso, tipo, historial y nivel de riesgo."
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
            texto={datos.diagnostico}
            nivel={
              actual?.nivelRiesgo === 'ALTO' ? 'error' : actual?.nivelRiesgo === 'MEDIO' ? 'alerta' : 'informacion'
            }
          />
          <Advertencias lista={datos.advertencias} />

          <RejillaIndicadores>
            <Indicador etiqueta="Base de datos" valor={texto(actual?.baseDatos)} />
            <Indicador
              etiqueta="Ultimo respaldo completo"
              valor={fechaHora(actual?.ultimoCompleto)}
              nota={actual?.horasDesdeCompleto != null ? `Hace ${entero(actual.horasDesdeCompleto)} h` : undefined}
            />
            <Indicador etiqueta="Ultimo diferencial" valor={fechaHora(actual?.ultimoDiferencial)} />
            <Indicador etiqueta="Ultimo log" valor={fechaHora(actual?.ultimoLog)} />
            <Indicador
              etiqueta="Nivel de riesgo"
              valor={texto(actual?.nivelRiesgo)}
              nivel={actual?.nivelRiesgo}
              nota={actual?.modeloRecuperacion ? `Modelo ${actual.modeloRecuperacion}` : undefined}
            />
          </RejillaIndicadores>

          <Panel titulo="Estado por base de datos" fuente="sys.databases · msdb.dbo.backupset" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'baseDatos', encabezado: 'Base de datos' },
                { clave: 'modeloRecuperacion', encabezado: 'Modelo' },
                { clave: 'ultimoCompleto', encabezado: 'Ultimo completo', presentar: (f) => fechaHora(f.ultimoCompleto) },
                { clave: 'ultimoDiferencial', encabezado: 'Ultimo diferencial', presentar: (f) => fechaHora(f.ultimoDiferencial) },
                { clave: 'ultimoLog', encabezado: 'Ultimo log', presentar: (f) => fechaHora(f.ultimoLog) },
                {
                  clave: 'nivelRiesgo',
                  encabezado: 'Riesgo',
                  presentar: (f) => <span className={claseMarca(f.nivelRiesgo)}>{f.nivelRiesgo}</span>
                },
                { clave: 'interpretacion', encabezado: 'Interpretacion' }
              ]}
              filas={datos.estadoPorBase}
              claveFila={(fila) => fila.baseDatos}
            />
          </Panel>

          <Panel titulo="Historial de respaldos" fuente="msdb.dbo.backupset · backupmediafamily" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'baseDatos', encabezado: 'Base de datos' },
                { clave: 'tipoRespaldo', encabezado: 'Tipo' },
                { clave: 'fin', encabezado: 'Finalizado', presentar: (f) => fechaHora(f.fin) },
                { clave: 'duracionSeg', encabezado: 'Duracion', tipo: 'numero', presentar: (f) => `${entero(f.duracionSeg)} s` },
                { clave: 'tamanoMb', encabezado: 'Tamano', tipo: 'numero', presentar: (f) => megabytes(f.tamanoMb) },
                {
                  clave: 'estado',
                  encabezado: 'Estado',
                  presentar: (f) => <span className={claseMarca('Exitoso')}>{f.estado}</span>
                },
                { clave: 'usuario', encabezado: 'Usuario', presentar: (f) => texto(f.usuario) },
                { clave: 'dispositivo', encabezado: 'Dispositivo', tipo: 'codigo', presentar: (f) => texto(f.dispositivo) }
              ]}
              filas={datos.historial}
              claveFila={(fila, indice) => indice}
              mensajeVacio="No hay respaldos registrados en msdb. Ejecute un respaldo completo para poblar el historial."
            />
          </Panel>

          <Panel titulo="Ejecuciones de jobs de respaldo" fuente="msdb.dbo.sysjobhistory" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'nombreJob', encabezado: 'Job' },
                { clave: 'paso', encabezado: 'Paso', presentar: (f) => texto(f.paso) },
                { clave: 'fechaEjecucion', encabezado: 'Ejecutado', presentar: (f) => fechaHora(f.fechaEjecucion) },
                {
                  clave: 'estado',
                  encabezado: 'Resultado',
                  presentar: (f) => <span className={claseMarca(f.estado)}>{f.estado}</span>
                },
                { clave: 'mensaje', encabezado: 'Mensaje', tipo: 'codigo' }
              ]}
              filas={datos.ejecucionesJobs}
              claveFila={(fila, indice) => indice}
              mensajeVacio="No hay jobs de respaldo programados en el Agente SQL Server."
            />
          </Panel>
        </>
      )}
    </>
  );
}
