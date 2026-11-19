# Conexión de repositorios GitHub

## Estado operativo del bloque actual (19/11/2026)

La GitHub App está configurada y su JWT se comprobó previamente con `GET /app` (HTTP 200). Una consulta directa de solo lectura a GitHub el 19/11/2026 confirmó la instalación `183677764` en `CondezJuan8`, permiso `Contents: Read`, token de instalación emitido (HTTP 201) y dos repositorios autorizados (HTTP 200). En la base de la plataforma **no hay todavía instalaciones ni repositorios vinculados**. La instalación hecha directamente en GitHub no crea por sí sola un vínculo con una organización de la plataforma.

La vista de repositorios ofrece **Conectar GitHub**. Si la App tiene exactamente una instalación no vinculada a la organización, el backend inicia directamente OAuth/PKCE para esa instalación. Si no hay ninguna o hay varias, abre la selección de instalación en GitHub; la sección **Vincular instalación existente** permite usar el ID concreto como alternativa. El callback exige la misma sesión y comprueba con GitHub que la identidad autorizada accede a esa instalación y que pertenece a esta App. Luego aparecerán los repositorios autorizados para importarlos. La vinculación real requiere que el propietario complete el consentimiento en su navegador; todavía no está acreditada por una prueba completa con GitHub real.

La captura del usuario mostraba el JavaScript anterior aún cargado en la pestaña y el registro HTTP confirmaba que **Instalar GitHub App** abrió GitHub sin llegar a `app-callback`; la base seguía en cero. Nginx ahora entrega `index.html` con `Cache-Control: no-store, must-revalidate`, y una página restaurada desde historial del navegador se recarga al volver. Tras desplegar una nueva versión, recarga la pestaña una vez antes de probar.

Los repositorios importados de GitHub y las carpetas locales se distinguen mediante `provider` en el catálogo y etiquetas en la interfaz. Ambos usan los mismos permisos por repositorio. `GET /api/organizations/{organizationId}/repositories/{repositoryId}/source` requiere `repository.read` y entrega un ZIP. Para local comprueba el SHA-256 guardado; para GitHub comprueba de nuevo el acceso de la instalación, fija el SHA del último commit de la rama predeterminada y obtiene un ZIP temporal limitado a 100 MiB. Responde con `X-Repository-Source`, `X-Source-SHA256` y, en GitHub, `X-Git-Commit`. Esto prepara una fuente reproducible para los próximos analizadores. El análisis Maven/npm y la extracción segura de ZIP siguen pendientes y no se ejecutan al importar.

La base se respaldó en `.tools/backups/dependency-before-account-cleanup.dump` (ignorado por Git) antes de eliminar cuentas de prueba. Permanecen activas únicamente `administrador@example.test` y `condez.juanp@gmail.com`. Mamitapuebla fue suspendida y su única membresía pasó a `LEFT`; cuatro cuentas se eliminaron físicamente. La quinta quedó como identidad histórica anonimizada, inactiva y sin credenciales para conservar cuatro eventos de auditoría.

## Estado

Implementado en el bloque de integración GitHub: instalación de una GitHub App, autorización de la instalación por el usuario autenticado, listado y selección de repositorios e importación de sus metadatos a una organización. La pantalla adapta su disposición a móvil y escritorio. La App ya existe y está instalada en GitHub; permanece pendiente completar el consentimiento desde la plataforma con la cuenta propietaria.

Esta función es distinta del inicio de sesión GitHub. OAuth de identidad permite entrar o vincular una cuenta; la GitHub App concede acceso limitado a repositorios.

## Configurar GitHub App local

En GitHub, bajo la cuenta u organización propietaria (prevista: `CondezJuan8`), crea una GitHub App desde **Settings → Developer settings → GitHub Apps** y define:

- Homepage URL: `http://localhost:8089`
- Setup URL: `http://localhost:8089/?view=repositories`
- User authorization callback URL: `http://localhost:8089/api/integrations/github/app-callback`
- Permisos de repositorio: **Contents: Read-only** y **Metadata: Read-only**.
- Selecciona **Only select repositories** al instalarla. La persona propietaria decide en GitHub qué repositorios autoriza.
- Desactiva webhooks durante este bloque; la recepción/validación de eventos aún no está implementada.

No se necesita solicitar autorización de usuario durante la instalación inicial: la plataforma inicia el flujo de autorización con PKCE después de recibir el retorno de instalación.

Completa las variables en el `.env` ignorado por Git. No compartas secretos en el repositorio, capturas o chat:

```dotenv
GITHUB_APP_ENABLED=true
GITHUB_APP_SLUG=slug-de-la-app
GITHUB_APP_ID=id-numerico-de-la-app
GITHUB_APP_CLIENT_ID=client-id-de-la-app
GITHUB_APP_CLIENT_SECRET=client-secret-de-la-app
GITHUB_APP_PRIVATE_KEY_BASE64=llave-privada-pem-codificada-en-base64
GITHUB_APP_CALLBACK_URL=http://localhost:8089/api/integrations/github/app-callback
```

El **App ID**, el **Client ID** y el secreto de cliente son valores diferentes. La llave privada PEM que genera GitHub se codifica completa en Base64 para la variable indicada; no se sube el archivo PEM. Los valores predeterminados dejan la función apagada. En el servidor se reemplazan las URLs `localhost` por el origen público HTTPS y se suministran secretos con el almacén seguro del entorno.

Para codificar la llave en PowerShell sin dejar el PEM en el repositorio:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('RUTA_PRIVADA\github-app.pem'))
```

Pega el resultado directamente en `.env`; no lo incluyas en una conversación ni en la bitácora.

## Flujo y datos

1. Un propietario de Dependency Intelligence elige una organización y comienza la instalación.
2. GitHub devuelve `installation_id`, `state` y acción a la URL de setup. La interfaz elimina esos parámetros de la barra de direcciones y los envía al backend.
3. El backend comprueba el `state` de un solo uso, ligado a usuario, organización, sesión de navegador y diez minutos, y genera PKCE S256.
4. Tras el consentimiento, el backend intercambia el código en el callback autenticado, valida con el token temporal que la identidad GitHub puede acceder a esa instalación, comprueba que la instalación pertenece a esta App y descarta el token de usuario. Los tokens no se persisten.
5. La API lista hasta 2.000 repositorios por instalación y permite seleccionar/importar hasta 500 a la vez. La base guarda metadatos (identificador, nombre, rama predeterminada, URL, visibilidad y estado archivado) en las tablas existentes. No descarga código, commits ni dependencias todavía.

La verificación de pertenencia es necesaria porque GitHub advierte que no debe confiarse en el `installation_id` recibido desde Setup URL sin verificarlo mediante una identidad de usuario. Los permisos y tokens de instalación siguen el alcance concedido a la App. [Seguridad de Setup URL](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/about-the-setup-url), [tokens de usuario de GitHub App](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app), [tokens de instalación](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-an-installation-access-token-for-a-github-app), [selección de permisos](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/choosing-permissions-for-a-github-app).

## Responsive y límites actuales

La vista usa primero una disposición móvil: encabezado apilado, selector a ancho completo, filas que pueden envolver texto y controles táctiles con altura mínima. En pantallas amplias, el encabezado y las acciones vuelven a distribuirse horizontalmente. Los estilos siguen los tokens oficiales del frontend.

Esto acredita estilos responsive en el código; no equivale a una comprobación visual en teléfono físico. `localhost` solo es accesible en el equipo que ejecuta la aplicación. Para que otros dispositivos se conecten hace falta desplegarla en una dirección de red/servidor accesible, HTTPS y callbacks GitHub con ese mismo origen. No se descargan ni escanean fuentes; el próximo tramo funcional es checkout seguro del código, jobs de análisis, Maven/npm, SBOM y resultados.

## Diagnostico de conexion - bloque 28

La pantalla consulta el estado seguro por organizacion y distingue configuracion incompleta de permisos insuficientes. La GitHub App de `CondezJuan8` ya esta creada y las variables locales completas estan en `.env`, ignorado por Git. La autenticacion de la App se verifico contra `GET https://api.github.com/app` (HTTP 200, App ID y slug coinciden). La instalacion en GitHub ya existe; falta completar el consentimiento OAuth/PKCE desde la plataforma. No poner secretos en Git/chat.

Para esta instancia se usa `http://localhost:8089`; Homepage, Setup URL y callback deben coincidir con ese origen, en especial `http://localhost:8089/api/integrations/github/app-callback`. Si se cambia `WEB_PORT`, actualizar en GitHub y `.env` el callback con el mismo puerto y reiniciar backend.

La App ya existe con slug `dependency-intelligence-platform`. El CTA **Instalar GitHub App** inicia el flujo backend y redirige a `https://github.com/apps/{slug}/installations/new`; la autorización OAuth/PKCE termina en el callback registrado. Crear la App, instalarla en una cuenta y seleccionar repositorios son pasos distintos.

En la misma pantalla, el propietario puede cargar un directorio desde su equipo. El navegador manda hasta 2.000 archivos y 100 MiB; se omiten `.git`, `node_modules`, `target`, `dist`, `build` y `.angular`. El backend valida rutas y empaqueta las fuentes en ZIP privado ligado a un repositorio `local`, dentro del volumen persistente `repository_data`. La organización puede asignar los mismos permisos de equipo/miembro. La descarga autorizada del ZIP está implementada; la carga todavía no inicia análisis Maven/npm.
