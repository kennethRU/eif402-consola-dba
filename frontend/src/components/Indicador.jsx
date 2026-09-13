/** Lectura individual dentro de la rejilla de indicadores. */
export function Indicador({ etiqueta, valor, nota, nivel }) {
  return (
    <div className="indicador" data-nivel={nivel}>
      <span className="indicador__etiqueta">{etiqueta}</span>
      <span className="indicador__valor">{valor}</span>
      {nota && <span className="indicador__nota">{nota}</span>}
    </div>
  );
}

export function RejillaIndicadores({ children }) {
  return <div className="rejilla-indicadores">{children}</div>;
}
