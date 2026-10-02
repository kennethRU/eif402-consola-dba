import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import Panel from '../components/Panel.jsx';
import Tabla from '../components/Tabla.jsx';
import EncabezadoModulo from '../components/EncabezadoModulo.jsx';
import { Indicador, RejillaIndicadores } from '../components/Indicador.jsx';
import { Advertencias, Cargando, Diagnostico, ErrorConsulta } from '../components/Estado.jsx';
import { entero, fechaHora, milisegundos, numero, recortar, texto } from '../components/Formato.js';
import { useRecurso } from '../hooks/useRecurso.js';

const CRITERIOS = [
  { valor: 'total_worker_time', etiqueta: 'Tiempo de CPU acumulado' },
  { valor: 'total_elapsed_time', etiqueta: 'Duracion acumulada' },
  { valor: 'total_logical_reads', etiqueta: 'Lecturas logicas' },
  { valor: 'execution_count', etiqueta: 'Cantidad de ejecuciones' }
];

/** Modulo 2 - Monitoreo de rendimiento. */
export default function ModuloRendimiento({ controles }) {
  const [criterio, setCriterio] = useState('total_worker_time');
  const [cantidad, setCantidad] = useState(15);

  const { datos, cargando, error, actualizado, recargar } = useRecurso('/rendimiento/resumen', {
    parametros: { ordenarPor: criterio, cantidad },
    intervaloMs: 20000
  });

  const grafico = (datos?.consultasCostosas ?? []).slice(0, 8).map((consulta, indice) => ({
    nombre: `#${indice + 1}`,
    cpu: Number(consulta.cpuTotalMs ?? 0),
    duracion: Number(consulta.duracionTotalMs ?? 0)
  }));

  return (
    <>
      <EncabezadoModulo
        titulo="Monitoreo de rendimiento"
        descripcion="Consultas de mayor consumo, tiempos de ejecucion, sesiones activas y bloqueos detectados en la instancia."
        actualizado={actualizado}
        alRecargar={recargar}
      >
        {controles}
      </EncabezadoModulo>

      <div className="fila-controles">
        <label className="campo">
          <span>Ordenar consultas por</span>
          <select value={criterio} onChange={(evento) => setCriterio(evento.target.value)}>
            {CRITERIOS.map((opcion) => (
              <option key={opcion.valor} value={opcion.valor}>
                {opcion.etiqueta}
              </option>
            ))}
          </select>
        </label>
        <label className="campo">
          <span>Cantidad de filas</span>
          <select value={cantidad} onChange={(evento) => setCantidad(Number(evento.target.value))}>
            {[10, 15, 25, 50].map((valor) => (
              <option key={valor} value={valor}>
                {valor}
              </option>
            ))}
          </select>
        </label>
      </div>

      {error && <ErrorConsulta error={error} alReintentar={recargar} />}
      {cargando && !datos && <Cargando />}

      {datos && (
        <>
          <Advertencias lista={datos.advertencias} />
          <Diagnostico
            texto={datos.diagnosticoBloqueos}
            nivel={datos.sesionesBloqueadas?.length > 0 ? 'alerta' : 'informacion'}
          />

          <RejillaIndicadores>
            <Indicador
              etiqueta="Consultas en cache"
              valor={entero(datos.resumenEjecucion?.consultasEnCache)}
            />
            <Indicador
              etiqueta="Ejecuciones totales"
              valor={entero(datos.resumenEjecucion?.ejecucionesTotales)}
            />
            <Indicador
              etiqueta="Duracion promedio"
              valor={milisegundos(datos.resumenEjecucion?.duracionPromedioMs)}
              nota="Por ejecucion registrada en el cache de planes"
            />
            <Indicador etiqueta="Sesiones activas" valor={entero(datos.sesionesActivas?.length)} />
            <Indicador
              etiqueta="Sesiones bloqueadas"
              valor={entero(datos.sesionesBloqueadas?.length)}
              nivel={datos.sesionesBloqueadas?.length > 0 ? 'ADVERTENCIA' : 'NORMAL'}
            />
          </RejillaIndicadores>

          {grafico.length > 0 && (
            <Panel titulo="Consumo de las consultas principales" fuente="sys.dm_exec_query_stats">
              <div className="grafico">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={grafico} margin={{ top: 8, right: 12, left: 4, bottom: 4 }}>
                    <CartesianGrid strokeDasharray="2 4" stroke="#dde5ea" vertical={false} />
                    <XAxis dataKey="nombre" tick={{ fontSize: 11, fontFamily: 'IBM Plex Mono' }} />
                    <YAxis
                      tick={{ fontSize: 11, fontFamily: 'IBM Plex Mono' }}
                      label={{ value: 'ms', angle: -90, position: 'insideLeft', fontSize: 11 }}
                    />
                    <Tooltip formatter={(valor) => `${numero(valor)} ms`} />
                    <Bar dataKey="cpu" name="CPU acumulada" fill="#0a6a70" />
                    <Bar dataKey="duracion" name="Duracion acumulada" fill="#8fb8bb" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </Panel>
          )}

          <Panel
            titulo="Consultas de mayor consumo"
            fuente="sys.dm_exec_query_stats · sys.dm_exec_sql_text"
            sinRelleno
          >
            <Tabla
              columnas={[
                { clave: 'baseDatos', encabezado: 'Base de datos', presentar: (f) => texto(f.baseDatos) },
                { clave: 'ejecuciones', encabezado: 'Ejec.', tipo: 'numero', presentar: (f) => entero(f.ejecuciones) },
                { clave: 'cpuTotalMs', encabezado: 'CPU total', tipo: 'numero', presentar: (f) => milisegundos(f.cpuTotalMs) },
                { clave: 'cpuPromedioMs', encabezado: 'CPU prom.', tipo: 'numero', presentar: (f) => milisegundos(f.cpuPromedioMs) },
                { clave: 'duracionPromedioMs', encabezado: 'Duracion prom.', tipo: 'numero', presentar: (f) => milisegundos(f.duracionPromedioMs) },
                { clave: 'lecturasLogicasPromedio', encabezado: 'Lecturas prom.', tipo: 'numero', presentar: (f) => entero(f.lecturasLogicasPromedio) },
                { clave: 'ultimaEjecucion', encabezado: 'Ultima ejecucion', presentar: (f) => fechaHora(f.ultimaEjecucion) },
                { clave: 'textoConsulta', encabezado: 'Sentencia', tipo: 'codigo', presentar: (f) => recortar(f.textoConsulta) }
              ]}
              filas={datos.consultasCostosas}
              claveFila={(fila, indice) => indice}
              mensajeVacio="El cache de planes esta vacio. Ejecute alguna carga de trabajo y vuelva a consultar."
            />
          </Panel>

          <Panel titulo="Sesiones activas" fuente="sys.dm_exec_sessions · sys.dm_exec_requests" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'idSesion', encabezado: 'SPID', tipo: 'numero' },
                { clave: 'usuario', encabezado: 'Usuario' },
                { clave: 'equipoCliente', encabezado: 'Equipo', presentar: (f) => texto(f.equipoCliente) },
                { clave: 'aplicacion', encabezado: 'Aplicacion', presentar: (f) => recortar(f.aplicacion, 40) },
                { clave: 'baseDatos', encabezado: 'Base de datos', presentar: (f) => texto(f.baseDatos) },
                { clave: 'estado', encabezado: 'Estado' },
                { clave: 'cpuMs', encabezado: 'CPU', tipo: 'numero', presentar: (f) => milisegundos(f.cpuMs) },
                { clave: 'memoriaKb', encabezado: 'Memoria', tipo: 'numero', presentar: (f) => `${entero(f.memoriaKb)} KB` },
                { clave: 'ultimaSolicitud', encabezado: 'Ultima solicitud', presentar: (f) => fechaHora(f.ultimaSolicitud) }
              ]}
              filas={datos.sesionesActivas}
              claveFila={(fila) => fila.idSesion}
              mensajeVacio="No hay sesiones de usuario conectadas."
            />
          </Panel>

          <Panel titulo="Sesiones bloqueadas" fuente="sys.dm_exec_requests" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'sesionBloqueada', encabezado: 'SPID bloqueado', tipo: 'numero' },
                { clave: 'sesionBloqueadora', encabezado: 'SPID bloqueador', tipo: 'numero' },
                { clave: 'usuarioBloqueador', encabezado: 'Usuario bloqueador', presentar: (f) => texto(f.usuarioBloqueador) },
                { clave: 'tipoEspera', encabezado: 'Tipo de espera', presentar: (f) => texto(f.tipoEspera) },
                { clave: 'tiempoEsperaMs', encabezado: 'Espera', tipo: 'numero', presentar: (f) => milisegundos(f.tiempoEsperaMs) },
                { clave: 'recursoEsperado', encabezado: 'Recurso', presentar: (f) => texto(f.recursoEsperado) },
                { clave: 'consultaBloqueada', encabezado: 'Sentencia bloqueada', tipo: 'codigo', presentar: (f) => recortar(f.consultaBloqueada, 160) }
              ]}
              filas={datos.sesionesBloqueadas}
              claveFila={(fila) => fila.sesionBloqueada}
              mensajeVacio="No hay sesiones bloqueadas en este momento."
            />
          </Panel>

          <Panel titulo="Principales tipos de espera" fuente="sys.dm_os_wait_stats" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'tipoEspera', encabezado: 'Tipo de espera' },
                { clave: 'tareasEnEspera', encabezado: 'Tareas', tipo: 'numero', presentar: (f) => entero(f.tareasEnEspera) },
                { clave: 'esperaTotalSeg', encabezado: 'Espera total (s)', tipo: 'numero', presentar: (f) => numero(f.esperaTotalSeg) },
                { clave: 'porcentaje', encabezado: '% del total', tipo: 'numero', presentar: (f) => `${numero(f.porcentaje)} %` }
              ]}
              filas={datos.principalesEsperas}
              claveFila={(fila) => fila.tipoEspera}
            />
          </Panel>
        </>
      )}
    </>
  );
}
