export interface ExternalAuthResult { message: string; error: boolean; }

const results: Record<string, ExternalAuthResult> = {
  signed_in: { message: '', error: false },
  linked: { message: 'Método de acceso vinculado correctamente.', error: false },
  unlinked: { message: 'Método desvinculado. Inicia sesión con uno de los métodos disponibles.', error: false },
  link_required: { message: 'Ya existe una cuenta con ese correo. Inicia sesión en ella y vincula el proveedor desde tus métodos de acceso.', error: true },
  identity_conflict: { message: 'Esa identidad ya está vinculada. Utiliza su cuenta o elige otra identidad.', error: true },
  email_required: { message: 'El proveedor no facilitó un correo disponible. En GitHub, comprueba tu correo principal y sus permisos.', error: true },
  email_verification_required: { message: 'Verifica tu correo antes de vincular otro método de acceso.', error: true },
  session_expired: { message: 'Vuelve a iniciar sesión antes de modificar tus métodos de acceso.', error: true },
  account_disabled: { message: 'No se pudo iniciar sesión con esa cuenta.', error: true },
  login_failed: { message: 'No se pudo completar el acceso con el proveedor. Inténtalo de nuevo.', error: true },
};

export function parseExternalAuthResult(fragment: string): ExternalAuthResult | null {
  const params = new URLSearchParams(fragment.replace(/^#/, ''));
  const code = params.get('external-auth');
  if (code === null || params.size !== 1) return null;
  return Object.hasOwn(results, code) ? results[code] : results['login_failed'];
}
