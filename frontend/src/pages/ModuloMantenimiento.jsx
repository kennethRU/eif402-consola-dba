import { useState } from 'react';
import Panel from '../components/Panel.jsx';
import Tabla from '../components/Tabla.jsx';
import EncabezadoModulo from '../components/EncabezadoModulo.jsx';
import { Indicador, RejillaIndicadores } from '../components/Indicador.jsx';
import { Advertencias, Cargando, Diagnostico, ErrorConsulta } from '../components/Estado.jsx';
import { claseMarca, entero, fechaHora, numero, porcentaje, texto } from '../components/Formato.js';
import { useRecurso } from '../hooks/useRecurso.js';
import { enviar } from '../api/cliente.js';

/** Modulo 6 - Mantenimiento preventivo. */
export default function ModuloMantenimiento({ controles }) {
  const { datos, cargando, error, actualizado, recargar } = useRecurso('/mantenimiento/resumen');

  const [esquema, setEsquema] = useState('');
  const [tabla, setTabla] = useState('');
  const [muestreoCompleto, setMuestreoCompleto] = useState(false);
  const [enEjecucion, setEnEjecucion] = useState(null);
  const [resultado, setResultado] = useState(null);

  /** Ejecuta una accion de mantenimiento y muestra su confirmacion. */
  async function ejecutar(nombre, ruta, cuerpo) {
    setEnEjecucion(nombre);
    setResultado(null);
    try {
      const respuesta = await enviar(ruta, cuerpo);
      setResultado(respuesta);
      recargar();
    } catch (fallo) {
      setResultado({
        exitoso: false,
        operacion: nombre,
        mensaje: fallo.message,
        detalle: fallo.sugerencia ? [fallo.sugerencia] : []
      });
    } finally {
      setEnEjecucion(null);
    }
  }

  return (
    <>
      <EncabezadoModulo
        titulo="Mantenimiento preventivo"
        descripcion="Recalculo de estadisticas, deteccion y recompilacion de objetos invalidos, y revision de la fragmentacion de indices."
        actualizado={actualizado}
        alRecargar={recargar}
      >
        {controles}
      </EncabezadoModulo>

      <Panel titulo="Acciones de mantenimiento" fuente="dba.usp_* · sp_refreshsqlmodule">
        <div className="fila-controles">
          <label className="campo">
            <span>Esquema (opcional)</span>
            <input
              type="text"
              value={esquema}
              placeholder="dbo"
              onChange={(evento) => setEsquema(evento.target.value)}
            />
          </label>
          <label className="campo">
            <span>Tabla (vacio = toda la base de datos)</span>
            <input
              type="text"
              value={tabla}
              placeholder="Cliente"
              onChange={(evento) => setTabla(evento.target.value)}
            />
          </label>
          <label className="campo">
            <span>Muestreo</span>
            <select
              value={muestreoCompleto ? 'completo' : 'automatico'}
              onChange={(evento) => setMuestreoCompleto(evento.target.value === 'completo')}
            >
              <option value="automatico">Automatico</option>
              <option value="completo">Completo (FULLSCAN)</option>
            </select>
          </label>

          <button
            type="button"
            className="boton boton--principal"
            disabled={enEjecucion !== null}
            onClick={() =>
              ejecutar('Recalculo de estadisticas', '/mantenimiento/estadisticas', {
                esquema: esquema || null,
                tabla: tabla || null,
                muestreoCompleto
              })
            }
          >
            {enEjecucion === 'Recalculo de estadisticas' ? 'Ejecutando…' : 'Recalcular estadisticas'}
          </button>

          <button
            type="button"
            className="boton"
            disabled={enEjecucion !== null}
            onClick={() => ejecutar('Validacion de objetos', '/mantenimiento/validacion', {})}
          >
            {enEjecucion === 'Validacion de objetos' ? 'Validando…' : 'Validar objetos'}
          </button>

          <button
            type="button"
            className="boton"
            disabled={enEjecucion !== null}
            onClick={() =>
              ejecutar('Recompilacion', '/mantenimiento/recompilacion', {
                esquema: esquema || null,
                objeto: null
              })
            }
          >
            {enEjecucion === 'Recompilacion' ? 'Recompilando…' : 'Recompilar pendientes'}
          </button>
        </div>

        {resultado && (
          <div className={`aviso ${resultado.exitoso ? 'aviso--informacion' : 'aviso--error'}`}>
            <div>
              <p className="aviso__titulo">
                {resultado.operacion}: {resultado.exitoso ? 'operacion completada' : 'operacion fallida'}
              </p>
              <p style={{ margin: '4px 0 0' }}>{resultado.mensaje}</p>
              {resultado.duracionMs != null && (
                <p style={{ margin: '4px 0 0' }}>Duracion: {numero(resultado.duracionMs)} ms</p>
              )}
              {resultado.detalle?.length > 0 && (
                <ul className="lista-advertencias" style={{ marginTop: 6 }}>
                  {resultado.detalle.map((linea) => (
                    <li key={linea} style={{ fontFamily: 'var(--dato)', fontSize: 11.5 }}>
                      {linea}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        )}
      </Panel>

      {error && <ErrorConsulta error={error} alReintentar={recargar} />}
      {cargando && !datos && <Cargando />}

      {datos && (
        <>
          <Diagnostico
            texto={datos.diagnostico}
            nivel={datos.diagnostico?.startsWith('Tareas pendientes') ? 'alerta' : 'informacion'}
          />
          <Advertencias lista={datos.advertencias} />

          <RejillaIndicadores>
            <Indicador etiqueta="Estadisticas revisadas" valor={entero(datos.estadisticas?.length)} />
            <Indicador
              etiqueta="Requieren recalculo"
              valor={entero(datos.estadisticas?.filter((e) => e.recomendacion?.startsWith('Recalcular')).length)}
              nivel={
                datos.estadisticas?.some((e) => e.recomendacion?.startsWith('Recalcular'))
                  ? 'ADVERTENCIA'
                  : 'NORMAL'
              }
            />
            <Indicador etiqueta="Indices analizados" valor={entero(datos.indices?.length)} />
            <Indicador
              etiqueta="Objetos invalidos"
              valor={entero(datos.objetosInvalidos?.length)}
              nivel={datos.objetosInvalidos?.length > 0 ? 'CRITICO' : 'NORMAL'}
            />
          </RejillaIndicadores>

          <Panel titulo="Objetos invalidos detectados" fuente="dba.objeto_invalido" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'esquema', encabezado: 'Esquema' },
                { clave: 'objeto', encabezado: 'Objeto' },
                { clave: 'tipoObjeto', encabezado: 'Tipo' },
                { clave: 'numeroError', encabezado: 'Error', tipo: 'numero', presentar: (f) => texto(f.numeroError) },
                { clave: 'mensajeError', encabezado: 'Mensaje', tipo: 'codigo' },
                { clave: 'fechaDeteccion', encabezado: 'Detectado', presentar: (f) => fechaHora(f.fechaDeteccion) }
              ]}
              filas={datos.objetosInvalidos}
              claveFila={(fila, indice) => indice}
              mensajeVacio="No hay objetos invalidos pendientes. Ejecute 'Validar objetos' para volver a comprobarlo."
            />
          </Panel>

          <Panel titulo="Estadisticas desactualizadas" fuente="sys.stats · sys.dm_db_stats_properties" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'esquema', encabezado: 'Esquema' },
                { clave: 'tabla', encabezado: 'Tabla' },
                { clave: 'estadistica', encabezado: 'Estadistica' },
                { clave: 'ultimaActualizacion', encabezado: 'Actualizada', presentar: (f) => fechaHora(f.ultimaActualizacion) },
                { clave: 'filas', encabezado: 'Filas', tipo: 'numero', presentar: (f) => entero(f.filas) },
                {
                  clave: 'modificacionesPendientes',
                  encabezado: 'Modif. pendientes',
                  tipo: 'numero',
                  presentar: (f) => entero(f.modificacionesPendientes)
                },
                {
                  clave: 'recomendacion',
                  encabezado: 'Recomendacion',
                  presentar: (f) => (
                    <span className={claseMarca(f.recomendacion === 'Sin accion' ? 'NORMAL' : 'MEDIO')}>
                      {f.recomendacion}
                    </span>
                  )
                }
              ]}
              filas={datos.estadisticas}
              claveFila={(fila, indice) => indice}
            />
          </Panel>

          <Panel titulo="Fragmentacion de indices" fuente="sys.dm_db_index_physical_stats (LIMITED)" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'esquema', encabezado: 'Esquema' },
                { clave: 'tabla', encabezado: 'Tabla' },
                { clave: 'indice', encabezado: 'Indice' },
                { clave: 'tipoIndice', encabezado: 'Tipo' },
                {
                  clave: 'fragmentacionPorcentaje',
                  encabezado: 'Fragmentacion',
                  tipo: 'numero',
                  presentar: (f) => porcentaje(f.fragmentacionPorcentaje)
                },
                { clave: 'paginas', encabezado: 'Paginas', tipo: 'numero', presentar: (f) => entero(f.paginas) },
                {
                  clave: 'recomendacion',
                  encabezado: 'Recomendacion',
                  presentar: (f) => (
                    <span className={claseMarca(f.recomendacion === 'Sin accion' ? 'NORMAL' : 'MEDIO')}>
                      {f.recomendacion}
                    </span>
                  )
                }
              ]}
              filas={datos.indices}
              claveFila={(fila, indice) => indice}
              mensajeVacio="No hay indices con mas de 100 paginas para analizar."
            />
          </Panel>

          <Panel titulo="Bitacora de mantenimiento" fuente="dba.bitacora_mantenimiento" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'fechaEjecucion', encabezado: 'Fecha', presentar: (f) => fechaHora(f.fechaEjecucion) },
                { clave: 'tipoOperacion', encabezado: 'Operacion' },
                { clave: 'objetoAfectado', encabezado: 'Objeto', presentar: (f) => texto(f.objetoAfectado) },
                {
                  clave: 'resultado',
                  encabezado: 'Resultado',
                  presentar: (f) => (
                    <span className={claseMarca(f.resultado === 'EXITOSO' ? 'NORMAL' : 'CRITICO')}>
                      {f.resultado}
                    </span>
                  )
                },
                { clave: 'duracionMs', encabezado: 'Duracion', tipo: 'numero', presentar: (f) => `${entero(f.duracionMs)} ms` },
                { clave: 'usuarioSolicitante', encabezado: 'Usuario' },
                { clave: 'mensaje', encabezado: 'Mensaje', tipo: 'codigo' }
              ]}
              filas={datos.bitacora}
              claveFila={(fila, indice) => indice}
              mensajeVacio="Aun no se ha ejecutado ninguna operacion de mantenimiento desde la herramienta."
            />
          </Panel>
        </>
      )}
    </>
  );
}
