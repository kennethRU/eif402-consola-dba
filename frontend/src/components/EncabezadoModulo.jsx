import { hora } from './Formato.js';

/** Cabecera comun de cada modulo: titulo, proposito y controles. */
export default function EncabezadoModulo({ titulo, descripcion, actualizado, alRecargar, children }) {
  return (
    <div className="encabezado-modulo">
      <div>
        <h1 className="encabezado-modulo__titulo">{titulo}</h1>
        <p className="encabezado-modulo__descripcion">{descripcion}</p>
      </div>
      <div className="encabezado-modulo__acciones">
        {children}
        {actualizado && <span className="sello-actualizacion">Consultado a las {hora(actualizado)}</span>}
        {alRecargar && (
          <button type="button" className="boton" onClick={alRecargar}>
            Actualizar
          </button>
        )}
      </div>
    </div>
  );
}
