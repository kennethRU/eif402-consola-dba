/**
 * Contenedor de una seccion de datos. La propiedad "fuente" documenta
 * en la propia interfaz de que vista o DMV proviene la informacion.
 */
export default function Panel({ titulo, fuente, acciones, sinRelleno = false, children }) {
  return (
    <section className="panel">
      <header className="panel__cabecera">
        <h2 className="panel__titulo">{titulo}</h2>
        {acciones}
        {fuente && <span className="panel__fuente">{fuente}</span>}
      </header>
      <div className={sinRelleno ? 'panel__cuerpo panel__cuerpo--sin-relleno' : 'panel__cuerpo'}>
        {children}
      </div>
    </section>
  );
}
