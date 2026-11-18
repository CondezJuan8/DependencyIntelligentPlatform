# Verificación de correo y recuperación de contraseña

Bloque 07, 15 de noviembre de 2026. Backend y formularios Angular para solicitar enlaces de verificación y recuperación, confirmarlos y revocar sesiones tras cambiar la contraseña. Se reutiliza `user_access_tokens` y V13 añade `users.credential_version` e índice de solicitudes.

## Uso local

Con el entorno del README configurado, ejecutar `docker compose up --build -d`. La aplicación abre en `http://localhost:8088` y Mailpit en `http://localhost:8025`. El puerto SMTP local es 1025. Las tres direcciones admiten los puertos definidos en `.env`.

1. Crear una cuenta de responsable. La aplicación conserva la solicitud y muestra **Esperando confirmación del propietario**; se puede cerrar la página y volver a iniciar sesión para consultar el estado.
2. El administrador de plataforma recibe en el correo configurado un aviso de solicitud nueva. En local, abrir Mailpit, iniciar sesión en la plataforma y elegir **Confirmar y aprobar responsable**. Esta acción confirma el correo y habilita el acceso; el nuevo responsable no necesita abrir un enlace de verificación.
3. Desde el formulario de login, elegir **Olvidé mi contraseña** e introducir el correo de una cuenta local.
4. Abrir el correo de recuperación en Mailpit y guardar una contraseña nueva de entre 15 y 128 caracteres. Las sesiones anteriores dejan de autorizar peticiones. Volver a iniciar sesión con la contraseña nueva.

Los enlaces se abren en Angular mediante fragmento de URL. Angular retira ese fragmento de la barra de direcciones y mantiene el token en memoria hasta confirmar o salir. Recargar antes de confirmar requiere reabrir el enlace desde el correo. Abrir el enlace solicita una confirmación explícita mediante POST con CSRF.

Mailpit captura correo de desarrollo y no entrega mensajes a Gmail ni a otros buzones externos. En Compose, abrir `http://localhost:8026` (o el puerto definido en `.env`) para revisar localmente el aviso al administrador. Para recibir ese aviso en un buzón real, configurar el servidor SMTP externo, credenciales y TLS del entorno autorizado. La publicación en servidor requiere fijar `PUBLIC_BASE_URL` con la dirección HTTPS de la aplicación, configurar SMTP y validar ese despliegue.

En desarrollo con `npm start`, configurar `PUBLIC_BASE_URL=http://localhost:4200` en el entorno del backend y `SMTP_HOST=localhost` para utilizar el SMTP publicado por Mailpit. El proxy Angular conecta la API en 8080. Al ejecutar solo el JAR, comprobar que PUBLIC_BASE_URL apunte al frontend que realmente se esté sirviendo.

## Configuración

| Variable | Valor inicial local | Función |
| --- | --- | --- |
| `SMTP_HOST` | `mailpit` en Compose, `localhost` al ejecutar el JAR | Servidor SMTP |
| `SMTP_PORT` | `1025` | Puerto del servidor SMTP |
| `SMTP_USER` / `SMTP_PASSWORD` | Vacíos | Credenciales externas |
| `SMTP_AUTH` | `false` | Activar autenticación SMTP |
| `SMTP_STARTTLS` | `false` | Activar y exigir STARTTLS cuando se establece true |
| `MAIL_FROM` | `no-reply@dependency.local` en Compose | Remitente |
| `PUBLIC_BASE_URL` | URL local con WEB_PORT en Compose, `http://localhost:8088` en JAR | Destino de los enlaces, validado al arrancar |
| `EMAIL_VERIFICATION_LIFETIME` | `PT24H` | Caducidad de verificación |
| `PASSWORD_RESET_LIFETIME` | `PT30M` | Caducidad de recuperación |
| `ACCOUNT_MAIL_COOLDOWN` | `PT60S` | Espera entre envíos de cada propósito para la misma cuenta |
| `MAILPIT_WEB_PORT` / `MAILPIT_SMTP_PORT` | `8025` / `1025` | Puertos de desarrollo publicados en 127.0.0.1 |

Los tiempos usan duraciones ISO-8601 y se validan al arrancar. El cliente SMTP tiene límites de conexión, lectura y escritura de tres segundos. El límite existente de peticiones de contraseña incluye los endpoints de ambos flujos.

Se incorporó `spring-boot-starter-mail` en Infrastructure, gestionado por el BOM existente. Mailpit se fija en v1.33.1 para desarrollo. Referencias de implementación en [Spring Boot email](https://docs.spring.io/spring-boot/reference/io/email.html), [Mailpit Docker](https://mailpit.axllent.org/docs/install/docker/) y [publicación v1.33.1](https://github.com/axllent/mailpit/releases/tag/v1.33.1).

## Contratos

| Método y ruta | Cuerpo | Acceso y resultado |
| --- | --- | --- |
| POST `/api/auth/email-verification/request` | `{}` | Sesión activa y CSRF, HTTP 202 |
| POST `/api/auth/email-verification/confirm` | `token` | CSRF y prueba del enlace, HTTP 204 |
| POST `/api/auth/password-reset/request` | `email` | CSRF, HTTP 202 uniforme para correos existentes y desconocidos |
| POST `/api/auth/password-reset/confirm` | `token`, `password` | CSRF y prueba del enlace, HTTP 204 |

`GET /api/auth/me` incorpora `emailVerified`. Los tokens inválidos, usados, caducados o de otro propósito devuelven 400 con detalle genérico. El envío de verificación puede devolver 503 si SMTP falla y el límite de peticiones puede devolver 429.

## Integridad y sesiones

Tokens de 32 bytes generados con SecureRandom, codificados como Base64 URL-safe. PostgreSQL conserva el SHA-256, propósito, creación, caducidad y consumo. Reenviar consume enlaces anteriores del mismo propósito. Los DTO redactan tokens y contraseñas en su representación textual.

Las operaciones se serializan mediante bloqueo de la fila del usuario. La confirmación vuelve a comprobar vigencia después del bloqueo. Cambiar contraseña actualiza hash y versión de credenciales, consume enlaces de recuperación e invalida metadatos de refresh existentes dentro de la transacción. El principal de sesión conserva la versión capturada junto al hash autenticado. Cada petición comprueba cuenta activa y versión vigente, por lo que una autenticación iniciada antes del cambio conserva una versión antigua que será rechazada.

Las cuentas externas sin credenciales locales quedan fuera de recuperación de contraseña. La confirmación de verificación prueba la posesión del enlace. El usuario puede iniciar sesión local mientras su correo está pendiente de verificar. La futura vinculación de proveedores e invitaciones debe exigir la prueba de identidad adecuada. Las decisiones de recuperación siguen [OWASP Forgot Password](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).

## Entrega y operación

La solicitud pública de recuperación entra en un ejecutor acotado de uno a dos hilos y 64 tareas en espera. La búsqueda de cuenta y el SMTP se realizan en segundo plano para mantener uniforme la respuesta pública. Una cola llena devuelve 429 antes de consultar la cuenta. Los fallos se registran mediante un código y tipo de excepción, con credenciales y destinatario excluidos del mensaje de diagnóstico.

La cola reside en memoria y se pierde si el proceso se interrumpe. Ante fallo de envío debe solicitarse otro enlace. El envío SMTP se realiza dentro de la transacción de emisión para que un error de transporte revierta la sustitución y preserve el enlace previo. SMTP y PostgreSQL son sistemas distintos. Si el servidor acepta el correo y después falla el commit, ese enlace puede resultar inválido y requerir un reenvío. La ejecución durable, reintentos y outbox para operación en servidor quedan pendientes.

## Verificación ejecutada

20 pruebas distintas pertinentes correctas. Siete de seguridad/límite de intentos/arquitectura, seis de AccountRecoveryIT, cinco de LocalAccessIT y dos métodos de migración/arranque de DatabaseIT. PostgreSQL 18.6 y Mailpit 1.33.1 nativos temporales, con login runtime limitado. Se comprobó SMTP real mediante la API de la bandeja y la existencia del hash confirmado en PostgreSQL, sin imprimir el token.

Cobertura de propósito, caducidad, reenvío/cooldown, dos confirmaciones concurrentes con un único ganador, rollback ante fallo de envío simulado, respuesta uniforme y revocación de dos sesiones. Una espera inicial de ocho segundos fue insuficiente para el correo asíncrono en este entorno. Se ajustó a veinte segundos manteniendo la condición de correo recibido y hash confirmado, y las seis pruebas de recuperación pasaron. Build Angular de producción correcto y cuatro aserciones del parser real de enlaces correctas.

La revisión visual sigue pendiente. En este bloque el host de navegador conectado continuó indisponible. Docker/Compose quedó sin ejecutar. Los servicios temporales se detienen al finalizar. La documentación, diccionario y diagramas proceden del catálogo V13 aplicado. El siguiente bloque es identidad Google/GitHub y vinculación segura, seguido de invitaciones y gestión de equipos/permisos.
