import { useState } from 'react';
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Legend,
  ResponsiveContainer, Tooltip, XAxis, YAxis
} from 'recharts';
import Panel from '../components/Panel.jsx';
import Tabla from '../components/Tabla.jsx';
import EncabezadoModulo from '../components/EncabezadoModulo.jsx';
import { Indicador, RejillaIndicadores } from '../components/Indicador.jsx';
import { Advertencias, Cargando, Diagnostico, ErrorConsulta, SinDatos } from '../components/Estado.jsx';
import { claseMarca, entero, megabytes, numero, porcentaje, texto } from '../components/Formato.js';
import { useRecurso } from '../hooks/useRecurso.js';
import { enviar } from '../api/cliente.js';

/** Modulo 3 - Gestion del almacenamiento. */
export default function ModuloAlmacenamiento({ controles }) {
  const { datos, cargando, error, actualizado, recargar } = useRecurso('/almacenamiento/resumen');
  const [registrando, setRegistrando] = useState(false);
  const [aviso, setAviso] = useState(null);

  async function registrarCaptura() {
    setRegistrando(true);
    setAviso(null);
    try {
      const resultado = await enviar('/almacenamiento/capturas');
      setAviso({
        tipo: 'informacion',
        texto: `Captura registrada: ${resultado?.archivos_registrados ?? 0} archivo(s). `
          + 'Repita la captura en distintos dias para construir la serie de crecimiento.'
      });
      recargar();
    } catch (fallo) {
      setAviso({ tipo: 'error', texto: fallo.message });
    } finally {
      setRegistrando(false);
    }
  }

  const serieFilegroups = (datos?.filegroups ?? []).map((fg) => ({
    nombre: fg.filegroup,
    usado: Number(fg.usadoMb ?? 0),
    libre: Number(fg.libreMb ?? 0)
  }));

  const serieCrecimiento = (datos?.crecimiento ?? []).map((punto) => ({
    fecha: punto.fecha,
    total: Number(punto.tamanoTotalMb ?? 0),
    usado: Number(punto.usadoMb ?? 0)
  }));

  return (
    <>
      <EncabezadoModulo
        titulo="Gestion del almacenamiento"
        descripcion="Espacio por filegroup, archivos de datos, objetos de mayor tamano y evolucion del crecimiento de la base de datos."
        actualizado={actualizado}
        alRecargar={recargar}
      >
        {controles}
        <button type="button" className="boton boton--principal" disabled={registrando} onClick={registrarCaptura}>
          {registrando ? 'Registrando…' : 'Registrar captura'}
        </button>
      </EncabezadoModulo>

      {aviso && <Diagnostico texto={aviso.texto} nivel={aviso.tipo} />}
      {error && <ErrorConsulta error={error} alReintentar={recargar} />}
      {cargando && !datos && <Cargando />}

      {datos && (
        <>
          <Advertencias lista={datos.advertencias} />
          <Diagnostico
            texto={datos.diagnostico}
            nivel={datos.diagnostico?.startsWith('Espacio critico') ? 'error' : 'informacion'}
          />

          <RejillaIndicadores>
            <Indicador etiqueta="Base de datos" valor={texto(datos.tamanoBaseDatos?.baseDatos)} />
            <Indicador etiqueta="Tamano total" valor={megabytes(datos.tamanoBaseDatos?.tamanoTotalMb)} />
            <Indicador etiqueta="Archivos de datos" valor={megabytes(datos.tamanoBaseDatos?.datosMb)} />
            <Indicador etiqueta="Registro de transacciones" valor={megabytes(datos.tamanoBaseDatos?.logMb)} />
            <Indicador etiqueta="Espacio realmente usado" valor={megabytes(datos.tamanoBaseDatos?.usadoMb)} />
          </RejillaIndicadores>

          <div className="rejilla-dos" style={{ marginBottom: 20 }}>
            <Panel titulo="Ocupacion por filegroup" fuente="sys.filegroups · FILEPROPERTY">
              {serieFilegroups.length === 0 ? (
                <SinDatos mensaje="No hay filegroups para mostrar." />
              ) : (
                <div className="grafico">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={serieFilegroups} margin={{ top: 8, right: 12, left: 4, bottom: 4 }}>
                      <CartesianGrid strokeDasharray="2 4" stroke="#dde5ea" vertical={false} />
                      <XAxis dataKey="nombre" tick={{ fontSize: 11, fontFamily: 'IBM Plex Mono' }} />
                      <YAxis tick={{ fontSize: 11, fontFamily: 'IBM Plex Mono' }} />
                      <Tooltip formatter={(valor) => `${numero(valor)} MB`} />
                      <Legend wrapperStyle={{ fontSize: 12 }} />
                      <Bar dataKey="usado" name="Usado (MB)" stackId="espacio" fill="#0a6a70" />
                      <Bar dataKey="libre" name="Libre (MB)" stackId="espacio" fill="#c9dcde" />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </Panel>

            <Panel titulo="Crecimiento de la base de datos" fuente={texto(datos.origenCrecimiento)}>
              {serieCrecimiento.length < 2 ? (
                <SinDatos mensaje="Se necesitan al menos dos capturas para trazar la evolucion. Use 'Registrar captura'." />
              ) : (
                <div className="grafico">
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={serieCrecimiento} margin={{ top: 8, right: 12, left: 4, bottom: 4 }}>
                      <CartesianGrid strokeDasharray="2 4" stroke="#dde5ea" vertical={false} />
                      <XAxis dataKey="fecha" tick={{ fontSize: 11, fontFamily: 'IBM Plex Mono' }} />
                      <YAxis tick={{ fontSize: 11, fontFamily: 'IBM Plex Mono' }} />
                      <Tooltip formatter={(valor) => `${numero(valor)} MB`} />
                      <Legend wrapperStyle={{ fontSize: 12 }} />
                      <Area type="monotone" dataKey="total" name="Tamano total (MB)" stroke="#0a6a70" fill="#d7ebec" />
                      <Area type="monotone" dataKey="usado" name="Usado (MB)" stroke="#a56a00" fill="#f6ead2" />
                    </AreaChart>
                  </ResponsiveContainer>
                </div>
              )}
            </Panel>
          </div>

          <Panel titulo="Espacio por filegroup" fuente="sys.filegroups · sys.database_files" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'filegroup', encabezado: 'Filegroup' },
                { clave: 'cantidadArchivos', encabezado: 'Archivos', tipo: 'numero' },
                { clave: 'tamanoMb', encabezado: 'Tamano', tipo: 'numero', presentar: (f) => megabytes(f.tamanoMb) },
                { clave: 'usadoMb', encabezado: 'Usado', tipo: 'numero', presentar: (f) => megabytes(f.usadoMb) },
                { clave: 'libreMb', encabezado: 'Libre', tipo: 'numero', presentar: (f) => megabytes(f.libreMb) },
                { clave: 'porcentajeUso', encabezado: 'Uso', tipo: 'numero', presentar: (f) => porcentaje(f.porcentajeUso) },
                {
                  clave: 'nivelAlerta',
                  encabezado: 'Nivel',
                  presentar: (f) => <span className={claseMarca(f.nivelAlerta)}>{f.nivelAlerta}</span>
                }
              ]}
              filas={datos.filegroups}
              claveFila={(fila) => fila.filegroup}
            />
          </Panel>

          <Panel titulo="Archivos de datos y registro" fuente="sys.database_files" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'nombreLogico', encabezado: 'Nombre logico' },
                { clave: 'tipo', encabezado: 'Tipo' },
                { clave: 'filegroup', encabezado: 'Filegroup', presentar: (f) => texto(f.filegroup) },
                { clave: 'tamanoMb', encabezado: 'Tamano', tipo: 'numero', presentar: (f) => megabytes(f.tamanoMb) },
                { clave: 'usadoMb', encabezado: 'Usado', tipo: 'numero', presentar: (f) => megabytes(f.usadoMb) },
                { clave: 'tamanoMaximo', encabezado: 'Maximo' },
                { clave: 'crecimientoConfigurado', encabezado: 'Crecimiento' },
                { clave: 'rutaFisica', encabezado: 'Ruta fisica', tipo: 'codigo' }
              ]}
              filas={datos.archivos}
              claveFila={(fila) => fila.idArchivo}
            />
          </Panel>

          <Panel titulo="Objetos con mayor consumo" fuente="sys.dm_db_partition_stats" sinRelleno>
            <Tabla
              columnas={[
                { clave: 'esquema', encabezado: 'Esquema' },
                { clave: 'objeto', encabezado: 'Tabla' },
                { clave: 'reservadoMb', encabezado: 'Reservado', tipo: 'numero', presentar: (f) => megabytes(f.reservadoMb) },
                { clave: 'usadoMb', encabezado: 'Usado', tipo: 'numero', presentar: (f) => megabytes(f.usadoMb) },
                { clave: 'filas', encabezado: 'Filas', tipo: 'numero', presentar: (f) => entero(f.filas) },
                { clave: 'cantidadIndices', encabezado: 'Indices', tipo: 'numero' }
              ]}
              filas={datos.objetosVoluminosos}
              claveFila={(fila) => `${fila.esquema}.${fila.objeto}`}
            />
          </Panel>
        </>
      )}
    </>
  );
}
