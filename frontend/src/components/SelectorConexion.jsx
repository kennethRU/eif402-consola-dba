import { useState } from 'react';
import { enviar } from '../api/cliente.js';

/**
 * Permite cambiar la instancia monitoreada en tiempo de ejecucion.
 * Los perfiles provienen de la configuracion del backend; la interfaz
 * nunca recibe ni envia contrasenas.
 */
export default function SelectorConexion({ perfiles, alCambiar }) {
  const activo = perfiles?.find((perfil) => perfil.activo);
  const [seleccion, setSeleccion] = useState(activo?.nombre ?? '');
  const [ocupado, setOcupado] = useState(false);
  const [mensaje, setMensaje] = useState(null);

  if (!perfiles || perfiles.length === 0) return null;

  async function aplicar(nombre) {
    setSeleccion(nombre);
    setOcupado(true);
    setMensaje(null);
    try {
      await enviar('/conexion/perfil-activo', { nombre });
      setMensaje({ tipo: 'ok', texto: 'Conexion cambiada.' });
      alCambiar?.();
    } catch (error) {
      setSeleccion(activo?.nombre ?? '');
      setMensaje({ tipo: 'error', texto: error.message });
    } finally {
      setOcupado(false);
      setTimeout(() => setMensaje(null), 4000);
    }
  }

  return (
    <label className="campo">
      <span>Instancia monitoreada</span>
      <select
        value={seleccion}
        disabled={ocupado}
        onChange={(evento) => aplicar(evento.target.value)}
      >
        {perfiles.map((perfil) => (
          <option key={perfil.nombre} value={perfil.nombre}>
            {perfil.nombre} · {perfil.servidor} / {perfil.baseDatos}
          </option>
        ))}
      </select>
      {mensaje && (
        <span style={{ color: mensaje.tipo === 'ok' ? 'var(--ok)' : 'var(--critico)' }}>
          {mensaje.texto}
        </span>
      )}
    </label>
  );
}
