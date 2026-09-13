/** Utilidades de presentacion compartidas por los modulos. */

const FORMATO_NUMERO = new Intl.NumberFormat('es-CR', { maximumFractionDigits: 2 });
const FORMATO_ENTERO = new Intl.NumberFormat('es-CR', { maximumFractionDigits: 0 });

export function numero(valor) {
  if (valor === null || valor === undefined) return '—';
  return FORMATO_NUMERO.format(valor);
}

export function entero(valor) {
  if (valor === null || valor === undefined) return '—';
  return FORMATO_ENTERO.format(valor);
}

/** Presenta megabytes en la unidad mas legible. */
export function megabytes(valor) {
  if (valor === null || valor === undefined) return '—';
  if (valor >= 1024 * 1024) return `${FORMATO_NUMERO.format(valor / 1024 / 1024)} TB`;
  if (valor >= 1024) return `${FORMATO_NUMERO.format(valor / 1024)} GB`;
  return `${FORMATO_NUMERO.format(valor)} MB`;
}

export function milisegundos(valor) {
  if (valor === null || valor === undefined) return '—';
  if (valor >= 60000) return `${FORMATO_NUMERO.format(valor / 60000)} min`;
  if (valor >= 1000) return `${FORMATO_NUMERO.format(valor / 1000)} s`;
  return `${FORMATO_NUMERO.format(valor)} ms`;
}

export function fechaHora(valor) {
  if (!valor) return '—';
  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) return '—';
  return fecha.toLocaleString('es-CR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
}

export function hora(valor) {
  if (!valor) return '—';
  return new Date(valor).toLocaleTimeString('es-CR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

export function porcentaje(valor) {
  if (valor === null || valor === undefined) return '—';
  return `${FORMATO_NUMERO.format(valor)} %`;
}

export function texto(valor, alterno = '—') {
  return valor === null || valor === undefined || valor === '' ? alterno : valor;
}

/** Recorta el texto de una consulta para la vista de tabla. */
export function recortar(valor, limite = 220) {
  if (!valor) return '—';
  const limpio = valor.replace(/\s+/g, ' ').trim();
  return limpio.length > limite ? `${limpio.slice(0, limite)}…` : limpio;
}

/** Traduce un nivel de alerta a la clase visual correspondiente. */
export function claseMarca(nivel) {
  switch (nivel) {
    case 'CRITICO':
    case 'ALTO':
    case 'Fallido':
      return 'marca marca--critico';
    case 'ADVERTENCIA':
    case 'MEDIO':
      return 'marca marca--alerta';
    case 'NORMAL':
    case 'BAJO':
    case 'Exitoso':
      return 'marca marca--ok';
    default:
      return 'marca marca--neutra';
  }
}
