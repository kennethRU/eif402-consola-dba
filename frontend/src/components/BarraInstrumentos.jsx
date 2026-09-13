import { useEffect, useState } from 'react';
import { megabytes, porcentaje, texto } from './Formato.js';

/**
 * Franja superior con las lecturas vivas de la instancia monitoreada.
 * El contador de actividad avanza en el cliente entre consultas para
 * reflejar que la instancia sigue en linea.
 */
export default function BarraInstrumentos({ instancia, conexionActiva, hayError }) {
  const [segundos, setSegundos] = useState(instancia?.segundosActividad ?? 0);

  useEffect(() => {
    setSegundos(instancia?.segundosActividad ?? 0);
  }, [instancia?.segundosActividad]);

  useEffect(() => {
    if (!instancia) return undefined;
    const temporizador = setInterval(() => setSegundos((valor) => valor + 1), 1000);
    return () => clearInterval(temporizador);
  }, [instancia]);

  const nivelMemoria = calcularNivel(instancia?.porcentajeMemoria);
  const enLinea = Boolean(instancia) && !hayError;

  return (
    <header className="regleta">
      <div className="regleta__marca">
        <span className="regleta__titulo">Consola DBA</span>
        <span className="regleta__subtitulo">EIF402 · Microsoft SQL Server</span>
      </div>

      <div className="lectura lectura--estado">
        <span
          className={`diodo ${enLinea ? 'diodo--activo' : 'diodo--fallo'}`}
          aria-hidden="true"
        />
        <div>
          <span className="lectura__etiqueta">Estado</span>
          <div className="lectura__valor">{enLinea ? 'EN LINEA' : 'SIN CONEXION'}</div>
        </div>
      </div>

      <div className="lectura">
        <span className="lectura__etiqueta">Instancia</span>
        <span className="lectura__valor">
          {texto(instancia?.nombreCompletoInstancia, conexionActiva ?? '—')}
        </span>
      </div>

      <div className="lectura">
        <span className="lectura__etiqueta">Version</span>
        <span className="lectura__valor">{texto(instancia?.versionProducto)}</span>
      </div>

      <div className="lectura">
        <span className="lectura__etiqueta">Actividad</span>
        <span className="lectura__valor">{formatearActividad(segundos)}</span>
      </div>

      <div className="lectura">
        <span className="lectura__etiqueta">
          Memoria {instancia?.porcentajeMemoria != null && `· ${porcentaje(instancia.porcentajeMemoria)}`}
        </span>
        <div className="barra-memoria">
          <div
            className="barra-memoria__relleno"
            data-nivel={nivelMemoria}
            style={{ width: `${Math.min(instancia?.porcentajeMemoria ?? 0, 100)}%` }}
          />
        </div>
        <span className="lectura__etiqueta">
          {megabytes(instancia?.memoriaUtilizadaMb)} de {megabytes(instancia?.memoriaObjetivoMb)}
        </span>
      </div>
    </header>
  );
}

function calcularNivel(valor) {
  if (valor == null) return 'normal';
  if (valor >= 95) return 'critico';
  if (valor >= 85) return 'alerta';
  return 'normal';
}

/** Convierte segundos a "Xd HH:MM:SS", el formato habitual de un tablero. */
function formatearActividad(totalSegundos) {
  if (!totalSegundos && totalSegundos !== 0) return '—';
  const dias = Math.floor(totalSegundos / 86400);
  const horas = Math.floor((totalSegundos % 86400) / 3600);
  const minutos = Math.floor((totalSegundos % 3600) / 60);
  const segundos = totalSegundos % 60;
  const reloj = [horas, minutos, segundos].map((parte) => String(parte).padStart(2, '0')).join(':');
  return dias > 0 ? `${dias}d ${reloj}` : reloj;
}
