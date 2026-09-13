/** Estados de carga, error y vacio, con instrucciones accionables. */

export function Cargando({ mensaje = 'Consultando la instancia' }) {
  return (
    <div className="estado-bloque">
      <div className="pulso" aria-hidden="true" />
      <p>{mensaje}…</p>
    </div>
  );
}

export function ErrorConsulta({ error, alReintentar }) {
  return (
    <div className="aviso aviso--error">
      <div>
        <p className="aviso__titulo">{error?.message ?? 'No fue posible obtener los datos.'}</p>
        {error?.sugerencia && <p style={{ margin: '4px 0 0' }}>{error.sugerencia}</p>}
        {alReintentar && (
          <button type="button" className="boton" style={{ marginTop: 10 }} onClick={alReintentar}>
            Reintentar
          </button>
        )}
      </div>
    </div>
  );
}

export function SinDatos({ mensaje }) {
  return <div className="estado-bloque">{mensaje}</div>;
}

export function Advertencias({ lista }) {
  if (!lista || lista.length === 0) return null;
  return (
    <div className="aviso aviso--alerta">
      <div>
        <p className="aviso__titulo">Notas sobre los datos mostrados</p>
        <ul className="lista-advertencias">
          {lista.map((texto) => (
            <li key={texto}>{texto}</li>
          ))}
        </ul>
      </div>
    </div>
  );
}

export function Diagnostico({ texto, nivel = 'informacion' }) {
  if (!texto) return null;
  return (
    <div className={`aviso aviso--${nivel}`}>
      <div>{texto}</div>
    </div>
  );
}
