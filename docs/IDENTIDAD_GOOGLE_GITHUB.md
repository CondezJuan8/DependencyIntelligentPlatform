# Acceso con Google y GitHub

Bloque 09, 15 de noviembre de 2026. Implementados backend y Angular para iniciar sesión, crear cuenta externa y vincular/desvincular identidades. Los proveedores permanecen deshabilitados hasta configurar credenciales propias. No se ha comprobado consentimiento real con Google/GitHub: no había credenciales configuradas en el entorno ni en `.env`.

## Activación local

1. Usar `.env.example` como referencia y conservar las contraseñas locales existentes. No publicar `.env` ni enviar secretos por chat.
2. Mantener `PUBLIC_BASE_URL=http://localhost:8088` si se utiliza Compose con el puerto predeterminado. Debe ser un origen HTTP(S), sin ruta, parámetros ni fragmento. Si cambia el puerto público, cambiar también esta variable y el callback registrado.
3. Registrar los clientes descritos abajo. Copiar sus identificadores y secretos a `.env` y habilitar únicamente el proveedor configurado.
4. Reconstruir/recrear los servicios: `docker compose up --build -d`. Este comando es la instrucción de operación; Compose no fue ejecutado como verificación del bloque.
5. Abrir `http://localhost:8088`. Los botones aparecen según `GET /api/auth/providers`. Completar primero acceso con una identidad nueva; después probar vinculación desde una cuenta local con correo verificado y sesión reciente.

```dotenv
PUBLIC_BASE_URL=http://localhost:8088
GOOGLE_LOGIN_ENABLED=true
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GITHUB_LOGIN_ENABLED=true
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=
OAUTH_AUTHORIZATION_LIFETIME=PT5M
OAUTH_REAUTHENTICATION_WINDOW=PT10M
```

Completar los campos vacíos con las credenciales del propietario. Habilitar un proveedor sin ID/secret válidos provoca fallo de arranque con mensaje de configuración; no hay credenciales de demostración. Los valores de cinco y diez minutos son parámetros iniciales configurables. Para Angular servido directamente en `http://localhost:4200`, usar ese origen en `PUBLIC_BASE_URL` y registrar callbacks con ese puerto, manteniendo el proxy de desarrollo de `/api`.

### Google

En [Google Auth Platform](https://console.cloud.google.com/auth/clients), configurar audiencia/consentimiento y un cliente OAuth de tipo aplicación web. Si la aplicación está en pruebas, incluir las cuentas de prueba que permita su configuración. Registrar exactamente:

```text
http://localhost:8088/api/auth/oauth2/callback/google
```

El backend solicita `openid profile email` y verifica el ID token mediante Spring Security: firma/JWKS, emisor, audiencia, caducidad y nonce. No depende del correo como identificador; utiliza `sub`. Se omite una consulta adicional a UserInfo porque las propiedades se obtienen del token firmado. [Referencia OIDC de Google](https://developers.google.com/identity/openid-connect/reference).

### GitHub

El cliente configurado usa el flujo web de autorización de GitHub. Puede registrarse una OAuth App para el acceso. Una GitHub App puede participar en autenticación de usuarios e instalación; no se presupone que hagan falta dos registros externos. La instalación para repositorios será un módulo posterior. [Autenticación de usuario de una GitHub App](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/authenticating-with-a-github-app-on-behalf-of-a-user).

Para OAuth App, en [Developer settings](https://github.com/settings/developers), indicar homepage `http://localhost:8088` y este Authorization callback URL:

```text
http://localhost:8088/api/auth/oauth2/callback/github
```

Se solicitan `read:user user:email`, sin scope `repo`. El backend usa el ID numérico estable de `/user` y obtiene el correo principal verificado desde `/user/emails`; no confía en el correo público del perfil. Se envía `X-GitHub-Api-Version: 2026-03-10`. Una identidad ya vinculada puede iniciar sesión aunque el proveedor deje de devolver su correo; una cuenta nueva necesita correo válido. [Flujo OAuth y PKCE](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps), [API de correos y permisos](https://docs.github.com/en/rest/users/emails?apiVersion=2022-11-28).

Si se elige una GitHub App, configurar los permisos de cuenta requeridos por el endpoint de correos según la referencia anterior. Los scopes OAuth no sustituyen esos permisos. El registro y consentimiento reales de ambos tipos de aplicación están pendientes de prueba con credenciales del propietario.

## Reglas de cuenta y seguridad

- Una identidad se reconoce por proveedor y sujeto, nunca por nombre de usuario o correo mutable. No se fusionan cuentas por coincidir el correo, incluso si el proveedor lo verifica: iniciar sesión en la cuenta existente y vincular explícitamente.
- Vincular exige cuenta local verificada, sesión reciente y CSRF. El callback debe pertenecer a esa misma identidad/sesión y versión de credenciales. Cambiar de cuenta durante el consentimiento cancela la vinculación.
- Google verifica automáticamente el correo local solo si el token firmado tiene `email_verified=true` y pertenece a Gmail o coincide con el dominio firmado `hd`. Otros correos requieren la verificación SMTP existente. [Criterio de autoridad del correo de Google](https://developers.google.com/identity/gsi/web/reference/html-reference).
- Google y GitHub usan PKCE S256. Inicio exclusivamente por POST con CSRF, state de un uso, nonce OIDC, caducidad y callback fijo por proveedor. Una sesión mantiene un flujo pendiente; iniciar otro sustituye el anterior. URL pública y redirects no se derivan de un Host aportado por el cliente.
- Al autenticar rotan ID de sesión y CSRF. Vincular incrementa la versión de credenciales y conserva la sesión actual actualizada; otras sesiones quedan revocadas. Desvincular requiere confirmación en UI, sesión reciente y otro método disponible; incrementa versión e invalida también la sesión actual. No se puede quitar el último método utilizable.
- Los tokens externos se usan para validar/leer identidad y se descartan. No se persisten como authorized clients en sesión o base, ni se entregan a Angular. Desvincular elimina el vínculo local; no revoca el consentimiento en la cuenta de Google/GitHub. Puede retirarse también desde los ajustes del proveedor.
- Un error de callback no sustituye ni elimina una sesión local vigente. Los mensajes de retorno son códigos estáticos permitidos; el frontend retira el fragmento de la URL. No se muestran errores ni secretos del proveedor.
- Nginx omite access logs en callbacks y usa `Referrer-Policy: no-referrer`. En servidor configurar HTTPS, `SESSION_COOKIE_SECURE=true`, origen público y callbacks exactos. Cualquier proxy/TLS adicional requiere revisar explícitamente sus cabeceras de confianza; no se habilita confianza global en cabeceras recibidas.

## Contratos

| Método y ruta | Acceso y resultado |
| --- | --- |
| `GET /api/auth/providers` | Público; Google/GitHub y disponibilidad, sin secretos |
| `POST /api/auth/providers/{provider}/login` | Anónimo + CSRF; URL de autorización |
| `GET /api/auth/oauth2/callback/{provider}` | Validación de protocolo y retorno al origen configurado |
| `GET /api/auth/providers/connections` | Sesión; vínculos, disponibilidad de contraseña y antigüedad |
| `POST /api/auth/providers/{provider}/link` | Sesión reciente, correo verificado y CSRF; URL de autorización |
| `POST /api/auth/providers/{provider}/unlink` | Sesión reciente y CSRF; 204 e invalidación de sesión |

`provider` admite `google` y `github`. La sesión de plataforma mantiene su principal propio, sin principal OAuth ni token externo. Domain/Application contienen contratos y reglas Java; Infrastructure adapta Spring Security y JDBC con transacciones/bloqueo de usuario. `user_identities` y `users.credential_version` ya existen en V5/V13: no se necesita migración ni tabla nueva. Permanecen 121 tablas.

## Evidencia y siguiente paso

Siete pruebas focalizadas de seguridad/arquitectura/límite y quince de integración pasaron: diez de identidad externa y cinco de acceso local. El proveedor HTTP local de prueba realiza intercambio real de código con PKCE y emite JWT RSA; no sustituye la validación con clientes reales. Se cubren firmas/issuer/audience/nonce/caducidad inválidos, state/proveedor/sesión incorrectos, colisiones de correo, concurrencia de creación, vínculos, revocación y último método. Build Angular de producción y cinco aserciones sobre el parser real de mensajes de retorno correctos. Revisión visual y Compose pendientes.

El recorrido de acceso y colores puede revisarse al levantar el entorno; los botones externos requieren activar credenciales. Invitaciones y gestión de equipos/permisos se entregaron después en el bloque 11: [recorrido](INVITACIONES_EQUIPOS_PERMISOS.md). Instalación GitHub y selección de repositorios siguen pendientes; elegir el siguiente bloque con el usuario. Aún no se cumple el hito GitHub → análisis → inventario con datos reales.
