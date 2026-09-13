import { SinDatos } from './Estado.jsx';

/**
 * Tabla generica. Cada columna declara su clave, encabezado, alineacion
 * y una funcion de presentacion opcional.
 */
export default function Tabla({ columnas, filas, claveFila, mensajeVacio = 'La consulta no devolvio filas.' }) {
  if (!filas || filas.length === 0) {
    return <SinDatos mensaje={mensajeVacio} />;
  }

  return (
    <div className="tabla-contenedor">
      <table className="tabla">
        <thead>
          <tr>
            {columnas.map((columna) => (
              <th key={columna.clave} className={columna.tipo === 'numero' ? 'numero' : undefined}>
                {columna.encabezado}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {filas.map((fila, indice) => (
            <tr key={claveFila ? claveFila(fila, indice) : indice}>
              {columnas.map((columna) => (
                <td
                  key={columna.clave}
                  className={
                    columna.tipo === 'numero' ? 'numero' : columna.tipo === 'codigo' ? 'codigo' : undefined
                  }
                >
                  {columna.presentar ? columna.presentar(fila) : fila[columna.clave] ?? '—'}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
