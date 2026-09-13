/**
 * Cliente HTTP de la consola. Centraliza el manejo de errores para que
 * todos los modulos reciban un mensaje comprensible en lugar de una
 * excepcion tecnica.
 */

const BASE = '/api';

class ErrorApi extends Error {
  constructor(mensaje, sugerencia, codigo) {
    super(mensaje);
    this.name = 'ErrorApi';
    this.sugerencia = sugerencia;
    this.codigo = codigo;
  }
}

async function procesar(respuesta) {
  if (respuesta.ok) {
    return respuesta.status === 204 ? null : respuesta.json();
  }
  let cuerpo = null;
  try {
    cuerpo = await respuesta.json();
  } catch {
    // La respuesta no traia JSON: se conserva el estado HTTP.
  }
  throw new ErrorApi(
    cuerpo?.mensaje ?? `La solicitud fallo con estado ${respuesta.status}.`,
    cuerpo?.sugerencia ?? 'Verifique que el backend este en ejecucion en el puerto 8080.',
    respuesta.status
  );
}

export async function obtener(ruta, parametros = {}) {
  const consulta = new URLSearchParams(
    Object.entries(parametros).filter(
      ([, valor]) => valor !== undefined && valor !== null && valor !== ''
    )
  ).toString();
  const url = consulta ? `${BASE}${ruta}?${consulta}` : `${BASE}${ruta}`;
  return procesar(await fetch(url));
}

export async function enviar(ruta, cuerpo = {}) {
  return procesar(
    await fetch(`${BASE}${ruta}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(cuerpo)
    })
  );
}

export { ErrorApi };
