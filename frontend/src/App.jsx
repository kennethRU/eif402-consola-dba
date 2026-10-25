import { useState } from 'react';
import BarraInstrumentos from './components/BarraInstrumentos.jsx';
import SelectorConexion from './components/SelectorConexion.jsx';
import ModuloInstancia from './pages/ModuloInstancia.jsx';
import ModuloRendimiento from './pages/ModuloRendimiento.jsx';
import ModuloAlmacenamiento from './pages/ModuloAlmacenamiento.jsx';
import ModuloRespaldos from './pages/ModuloRespaldos.jsx';
import ModuloAuditoria from './pages/ModuloAuditoria.jsx';
import ModuloMantenimiento from './pages/ModuloMantenimiento.jsx';
import { useRecurso } from './hooks/useRecurso.js';

/**
 * Los seis modulos siguen el orden establecido en la especificacion del
 * proyecto. La numeracion no es decorativa: identifica al modulo en la
 * documentacion, en los scripts SQL y en el reparto de trabajo del equipo.
 */
const MODULOS = [
  { indice: 'M1', clave: 'instancia', nombre: 'Instancia', componente: ModuloInstancia },
  { indice: 'M2', clave: 'rendimiento', nombre: 'Rendimiento', componente: ModuloRendimiento },
  { indice: 'M3', clave: 'almacenamiento', nombre: 'Almacenamiento', componente: ModuloAlmacenamiento },
  { indice: 'M4', clave: 'respaldos', nombre: 'Respaldos', componente: ModuloRespaldos },
  { indice: 'M5', clave: 'auditoria', nombre: 'Auditoria', componente: ModuloAuditoria },
  { indice: 'M6', clave: 'mantenimiento', nombre: 'Mantenimiento', componente: ModuloMantenimiento }
];

export default function App() {
  const [moduloActivo, setModuloActivo] = useState('instancia');

  // El estado de la instancia alimenta la regleta superior y el modulo 1.
  const recursoInstancia = useRecurso('/instancia/estado', { intervaloMs: 30000 });
  const perfiles = useRecurso('/conexion/perfiles');

  const modulo = MODULOS.find((entrada) => entrada.clave === moduloActivo) ?? MODULOS[0];
  const Componente = modulo.componente;

  const perfilActivo = perfiles.datos?.find((perfil) => perfil.activo);

  const selector = (
    <SelectorConexion
      perfiles={perfiles.datos}
      alCambiar={() => {
        perfiles.recargar();
        recursoInstancia.recargar();
      }}
    />
  );

  return (
    <div className="consola">
      <BarraInstrumentos
        instancia={recursoInstancia.datos?.instancia}
        conexionActiva={perfilActivo ? `${perfilActivo.servidor} / ${perfilActivo.baseDatos}` : null}
        hayError={Boolean(recursoInstancia.error)}
      />

      <nav className="rail" aria-label="Modulos de la consola">
        <div className="rail__grupo">
          <span className="rail__leyenda">Modulos</span>
          {MODULOS.map((entrada) => (
            <button
              key={entrada.clave}
              type="button"
              className="rail__enlace"
              aria-current={entrada.clave === moduloActivo}
              onClick={() => setModuloActivo(entrada.clave)}
            >
              <span className="rail__indice">{entrada.indice}</span>
              <span className="rail__nombre">{entrada.nombre}</span>
            </button>
          ))}
        </div>

        <div className="rail__pie">
          <p>
            Toda la informacion proviene de vistas del catalogo y DMV de SQL Server. Cada panel indica
            su fuente.
          </p>
        </div>
      </nav>

      <main className="contenido">
        {modulo.clave === 'instancia' ? (
          <Componente recurso={recursoInstancia} controles={selector} />
        ) : (
          <Componente controles={selector} />
        )}
      </main>
    </div>
  );
}
