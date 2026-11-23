# Acceso local y autorización

Actualizado el 15 de noviembre de 2026. El bloque 06 entrega registro, login, logout, restauración de sesión, creación de organización con propietario y consulta de organizaciones propias. El bloque 07 añade verificación y recuperación mediante enlaces SMTP. Procedimiento en [VERIFICACION_Y_RECUPERACION](VERIFICACION_Y_RECUPERACION.md).

## Recorrido de usuario

1. Configurar `.env` con las credenciales de base indicadas en el README. Para un volumen anterior, ejecutar el aprovisionamiento del login runtime antes del backend.
2. Arrancar con `docker compose up --build -d` y abrir `http://localhost:8088`.
3. Crear cuenta con nombre, correo y contraseña de entre 15 y 128 caracteres. La aplicación inicia sesión y muestra las organizaciones de la cuenta.
4. Crear una organización. La misma transacción crea organización, membresía OWNER, asignación del rol y evento de auditoría. El identificador/slug se genera en el servidor.
5. Cerrar sesión y volver a entrar. Recargar la página recupera la sesión mientras siga vigente. Los errores de conexión incluyen reintento.

Para desarrollo sin Docker, iniciar el JAR con las dos credenciales de base separadas según `database/README.md`. Desde `frontend/`, `npm ci` y `npm start` sirven Angular con proxy `/api` hacia `127.0.0.1:8080`. Node y versiones de herramientas se describen en el stack. El build de producción y las pruebas nativas pasaron en este bloque. El arranque mediante imágenes Compose queda por verificar.

## Contratos HTTP

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | `/api/auth/csrf` | Token CSRF enmascarado y nombre de su cabecera |
| POST | `/api/auth/register` | Cuenta creada y sesión iniciada, HTTP 201 |
| POST | `/api/auth/login` | Sesión iniciada, HTTP 200 |
| GET | `/api/auth/me` | Cuenta activa de la sesión, HTTP 200 |
| POST | `/api/auth/logout` | Sesión invalidada y cookie eliminada, HTTP 204 |
| GET | `/api/organizations` | Organizaciones activas de las membresías activas del usuario |
| POST | `/api/organizations` | Organización nueva con propietario autenticado, HTTP 201 |
| POST | `/api/organizations/{organizationId}/repositories` | Registro sujeto a `repository.manage`, HTTP 201 |
| GET | `/api/organizations/{organizationId}/repositories/{repositoryId}` | Consulta sujeta a `repository.read` |
| POST | `/api/organizations/{organizationId}/repositories/{repositoryId}/scans` | Solicitud sujeta a `scan.create`, HTTP 202 y estado PENDING |

Register recibe `email`, `displayName`, `password`. Login recibe `email`, `password`. Crear organización recibe `name`. Contratos de repositorios/scans conservan los DTO existentes. Un cliente HTTP debe mantener la cookie, obtener CSRF y enviar la cabecera devuelta en cada POST. Obtener un token nuevo después del login/logout. Angular realiza esa obtención antes de cada escritura.

Las respuestas distinguen 400 para datos inválidos, 401 para credenciales/sesión, 403 para autorización/CSRF, 409 para registro duplicado y 429 con Retry-After para límite de intentos. Un correo desconocido y una contraseña incorrecta devuelven el mismo detalle de login. La normalización de correo y su UNIQUE resuelven duplicados concurrentes. Los errores de validación y los DTO redactan credenciales.

## Sesiones y contraseñas

Se eligió PBKDF2-HMAC-SHA256 de Spring Security con 600.000 iteraciones, salt aleatorio de 16 bytes y salida de 256 bits. El prefijo identifica sus parámetros para evolución posterior. El verificador procesa un hash ficticio para cuentas inexistentes con formato de correo válido. La biblioteca ya está incluida en Spring Security. Referencia técnica utilizada para parámetros de PBKDF2, [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

La sesión reside en memoria del backend y se pierde al reiniciarlo. La cookie JSESSIONID usa HttpOnly y SameSite=Lax. Su ID rota al autenticar. El token CSRF rota al autenticar y la sesión se invalida al salir. Se comprueba que la cuenta siga activa en cada petición autenticada. La aplicación mantiene en memoria del navegador los datos públicos de la cuenta. Referencias de implementación, [persistencia de autenticación](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html) y [CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html).

| Configuración | Valor local inicial | Efecto |
| --- | --- | --- |
| `SESSION_TIMEOUT` | `30m` | Inactividad máxima de sesión |
| `SESSION_COOKIE_SECURE` | `false` | Compatible con HTTP local. Establecer `true` al servir mediante HTTPS |
| `AUTH_MAX_ATTEMPTS_PER_MINUTE` | `20` | Presupuesto conjunto de register/login por dirección remota y minuto |

El límite de intentos se mantiene por proceso con mapa acotado a 4.096 direcciones por ventana. Se reinicia con el backend. Tras Nginx, las peticiones pueden compartir la dirección del proxy y el presupuesto. La operación en servidor requiere concretar proxy de confianza y límites coordinados según despliegue.

## Autorización y RLS

Cada operación de repositorio verifica cuenta activa, organización activa, membresía activa y permiso del rol. OWNER/ADMIN tienen alcance sobre los repositorios de su organización. MEMBER/VIEWER necesitan además el mismo permiso asignado a un equipo al que pertenecen para el repositorio solicitado. Un permiso de equipo se intersecta con los permisos del rol. Por ejemplo, VIEWER conserva el límite que impide solicitar scans aunque un equipo tenga `scan.create`.

La comprobación y el caso de uso comparten transacción tenant. La revocación se consulta en cada petición. V12 permite descubrir las propias organizaciones activas mediante `app.user_id` local a una transacción de consulta sin organización seleccionada. El contexto se obtiene de la sesión autenticada. Las políticas existentes siguen gobernando operaciones tenant. RLS se basa en contextos fijados por el backend autorizado.

## Estado y validación

Pruebas focalizadas de HTTP/CSRF/sesión/autorización con PostgreSQL nativo 18.6 y login runtime real, actualización desde V1, RLS y límites de capas. Build Angular de producción correcto. Health de la API respondió UP, frontend HTTP 200 y cabecera real de cookie comprobada con HttpOnly/SameSite=Lax. La revisión visual en navegador quedó pendiente por indisponibilidad del host de browser y timeout de autorización al acceder a Chrome.

Verificación y recuperación se entregaron en el bloque 07. La cuenta expone emailVerified y la versión de credenciales revoca sesiones tras recuperar contraseña. El bloque 09 incorpora Google/GitHub y vínculo explícito, probados con proveedor HTTP local; requieren configurar clientes reales antes de habilitar botones. [Activación y contratos](IDENTIDAD_GOOGLE_GITHUB.md). El bloque 11 añade [invitaciones y gestión HTTP/UI de membresías/equipos/permisos](INVITACIONES_EQUIPOS_PERMISOS.md), con asignaciones y revocaciones verificadas mediante API y correo SMTP real en Mailpit. Continúan UI de repositorios/scans, sesiones persistentes y entrega durable para servidor. La revisión visual conjunta sigue pendiente; elegir el siguiente bloque con el usuario según PENDIENTES.

## Bootstrap local del primer administrador

Antes de registrar cualquier cuenta, crea la primera cuenta de plataforma en la base local vacia. Este mecanismo no es una ruta web: Compose ejecuta un servicio opt-in sin puerto HTTP, usa el acceso de migracion solo durante esa tarea y valida dentro de una transaccion con bloqueo que no existan usuarios, organizaciones ni administradores. Si encuentra datos, cancela sin modificarlos; la segunda ejecucion tambien se rechaza.

En PowerShell, desde la carpeta del proyecto:

```powershell
docker compose build platform-admin-bootstrap
docker compose run --rm -it platform-admin-bootstrap
```

El primer comando solo compila la imagen del operador y no vuelve a ejecutar la suite completa; las pruebas focalizadas ya se ejecutaron durante el bloque. El servicio solicita escribir `CREAR ADMIN`, nombre, correo, contrasena inicial de 15 a 128 caracteres y su confirmacion. La contrasena se captura sin eco y no se pone en `.env`, argumentos del comando, documentos ni Git. El usuario creado queda OWNER, activo, aprobado y con correo verificado; se guarda con el hash PBKDF2 vigente, se agrega a `platform_administrators` y se registra auditoria. Al terminar, el proceso se cierra. Luego inicia sesion en `http://localhost:8089`, crea la organizacion y conecta la GitHub App desde Mis repositorios.

Este comando esta previsto para la primera instalacion local. Ejecutalo antes de abrir el registro o exponer el sistema a otras personas. No uses un registro publico para intentar obtener permisos de administrador: el registro normal nunca puede asignar ese rol.
