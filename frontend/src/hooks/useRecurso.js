import { useCallback, useEffect, useRef, useState } from 'react';
import { obtener } from '../api/cliente.js';

/**
 * Carga un recurso de la API y expone su estado de carga, error y datos.
 * Soporta refresco manual y automatico por intervalo.
 */
export function useRecurso(ruta, { parametros = {}, intervaloMs = 0, activo = true } = {}) {
  const [datos, setDatos] = useState(null);
  const [cargando, setCargando] = useState(activo);
  const [error, setError] = useState(null);
  const [actualizado, setActualizado] = useState(null);

  const parametrosSerializados = JSON.stringify(parametros);
  const montado = useRef(true);

  const cargar = useCallback(
    async (silencioso = false) => {
      if (!activo) return;
      if (!silencioso) setCargando(true);
      try {
        const respuesta = await obtener(ruta, JSON.parse(parametrosSerializados));
        if (!montado.current) return;
        setDatos(respuesta);
        setError(null);
        setActualizado(new Date());
      } catch (fallo) {
        if (montado.current) setError(fallo);
      } finally {
        if (montado.current) setCargando(false);
      }
    },
    [ruta, parametrosSerializados, activo]
  );

  useEffect(() => {
    montado.current = true;
    cargar();
    return () => {
      montado.current = false;
    };
  }, [cargar]);

  useEffect(() => {
    if (!intervaloMs || !activo) return undefined;
    const temporizador = setInterval(() => cargar(true), intervaloMs);
    return () => clearInterval(temporizador);
  }, [intervaloMs, cargar, activo]);

  return { datos, cargando, error, actualizado, recargar: () => cargar(false) };
}
