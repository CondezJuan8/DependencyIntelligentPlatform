# Base de datos de Dependency Intelligence

PostgreSQL **17.7**, Flyway, Java 21/Spring Boot 3.5.16. Esquema en inglés, plural, UUID, `timestamptz`. La V1 original se conserva sin cambios.

## Arranque local

```bash
cp -n .env.example .env
docker compose up --build -d
```

En un `.env` anterior, añadir `APP_DB_PASSWORD` y `MIGRATION_DB_PASSWORD` con valores independientes. No cambiar `POSTGRES_PASSWORD` de un volumen ya inicializado sin rotarlo también en PostgreSQL. Las credenciales quedan fuera de Git. El bootstrap idempotente configura los roles también en volúmenes existentes; no depende de scripts de initdb que solo corren una vez.

El frontend sigue en http://localhost:8088 y `/health` comprueba la conexión. PostgreSQL no publica puertos. No se crea ningún usuario, organización o contraseña de aplicación en los seeds.

## Fases y migraciones

| Perfil | Ubicaciones Flyway | Tablas | Uso |
|---|---|---:|---|
| `core` (por defecto) | `db/migration` | 76 | Identidad/RBAC, integraciones, jobs, scans, SBOM, paquetes, vulnerabilidades, comparación, reportes, auditoría |
| `future` | core + `db/future` | 115 | Documentación, modelos/citas, impacto, notificaciones, programación |
| vector opcional | core + future + `db/vector` | 116 | Embeddings, solo con extensión instalada por administrador |

Los recuentos excluyen `flyway_schema_history`. Consultar el reporte de implementación para los recuentos comprobados. El `.env` local de este trabajo usa `future`, sin pgvector ni proveedores externos.

Para añadir las tablas futuras configurar `SPRING_PROFILES_ACTIVE=future` en `.env` y ejecutar `docker compose up -d backend`. Tras aplicar migraciones futuras, conservar ese perfil: Flyway valida también la presencia de sus archivos en siguientes arranques. No se habilita `outOfOrder` ni se modifica una migración aplicada.

- V1: inicialización previa, intacta.
- V2–V7: esquema core, referencias, RLS y estados de proceso.
- V101–V104: esquema futuro, RLS y estados.
- V201: embeddings opcionales. Requiere que un administrador haya instalado `vector`; no concede superusuario al migrador.

El esquema prepara capacidades; no implementa scanners, workers, APIs ni reglas de matching. Estados de proceso usan CHECK y trigger de transición. La deduplicación de scans usa `request_key` por repositorio; el caso de uso debe construir esa clave con commit/perfil/tipo para eventos repetidos y permitir una nueva solicitud explícita cuando corresponda. No se presume que cada commit admita solo un análisis.

## Roles y aislamiento

- Administrador local (`POSTGRES_USER`): bootstrap de roles, creación inicial de identidades y mantenimiento explícito; nunca datasource de la API.
- `dependency_migrator`: LOGIN independiente, propietario de tablas, permisos DDL, sin superusuario, creación de roles ni BYPASSRLS. Solo Flyway usa su contraseña.
- `dependency_app`: LOGIN independiente, no propietario, sin DDL ni BYPASSRLS. DML sobre tablas tenant, lectura de catálogos. Auditoría solo SELECT/INSERT. Sin acceso al historial Flyway ni a `user_identities`.

RLS se activa con FORCE. En tablas tenant se compara `organization_id` con `app.current_organization_id()`. En `organizations` se compara `id`. `users` es global, pero su lectura se limita a identidades con membresía activa en el tenant actual. No hay acceso runtime a sus hashes sin esta política; al implementar endpoints se deben seleccionar DTOs sin `password_hash`. Los catálogos y advisories públicos son globales de solo lectura para runtime; su ingesta necesita un proceso con privilegios específicos que deberá configurarse al integrar OSV. No entregar credenciales de administrador a la API.

El catálogo de componentes es **privado por organización**, para no revelar nombres de paquetes internos. Paquete, versión y aparición en scan permanecen separados. Las FKs compuestas preservan tenant y, donde corresponde, scan o repositorio. El borrado usa RESTRICT para impedir cascadas accidentales sobre historia.

## Uso desde Spring

`TenantTransactions.execute(organizationId, useCase)` crea una transacción nueva y ejecuta `set_config('app.organization_id', ?, true)` con binding JDBC. El contexto expira en COMMIT/ROLLBACK y no queda en la conexión del pool. Si se invoca dentro de otro caso de uso, la transacción exterior queda suspendida y conserva su tenant; evitar anidación por costo de conexiones y por independencia de commits.

El identificador debe venir de identidad y membership **ya validadas**, nunca de un header/UUID arbitrario. La API todavía deniega endpoints de negocio. RLS previene consultas sin filtro y cruces por FK; no es autenticación: el propio login SQL puede establecer el contexto. No se debe conceder acceso SQL directo a usuarios finales. Conversations, documentos y reportes también requieren permisos de usuario/recurso en aplicación antes de consultarlos.

```java
tenantTransactions.execute(organizationId, () -> repositoryUseCase.execute(command));
```

No se generan entidades JPA vacías para cada tabla. Se implementarán adaptadores y mappings por agregado cuando exista su caso de uso; el dominio sigue libre de JPA. Para lecturas masivas, usar proyecciones, batch y paginación por cursor.

## Documentos y vector opcional

`document_versions` admite solo una referencia temporal con expiración; si el original se marca eliminado esa referencia debe ser NULL. La fecha no elimina archivos por sí sola: falta el worker. No se configura una retención arbitraria. Hash, texto y citas no sustituyen el PDF original para reindexar o verificar firmas/imágenes.

V201 usa `vector` sin dimensión fija, con trigger que compara `vector_dims` con el modelo registrado. Cada modelo tiene dimensión inmutable; una dimensión nueva requiere otro modelo. No se elige proveedor ni dimensión de producción. La búsqueda debe filtrar tenant, permisos y modelo antes de comparar vectores. No se crea HNSW hasta seleccionar modelo/dimensión y medir volumen; entonces añadir índice parcial por modelo con cast `vector(N)`. Estrategia descrita en la [documentación oficial de pgvector](https://github.com/pgvector/pgvector#can-i-store-vectors-with-different-dimensions-in-the-same-column).

La validación vectorial usa una pila temporal con `pgvector/pgvector:0.8.1-pg17`, sin publicar puertos ni montar el volumen local. No cambiar la imagen/volumen de trabajo para ejecutar esa prueba.

## Verificación reproducible

```bash
docker compose build backend
bash tools/database/verify.sh
python3 tools/database/fresh.py
python3 tools/database/vector.py
python3 tools/database/render_docs.py
```

`verify.sh` detecta fases instaladas y ejecuta fixtures dentro de una transacción revertida. Usa el administrador solo para preparar fixtures, cambia realmente a `dependency_app` para probar RLS y verifica también permisos administrativos de ese rol. `fresh.py` crea una base temporal con nombre aleatorio en el servidor local, instala todo desde cero, prueba y elimina solo esa base. `vector.py` crea y elimina una pila independiente y exporta su catálogo real para documentación. No ejecutar estas herramientas contra entornos ajenos al Compose de desarrollo.

## Artefactos

- [Diccionario completo](data-dictionary.md): columnas, constraints, índices, alcance y relaciones reales.
- [Normalización](normalization.md): claves, dependencias y límites de las afirmaciones de 5NF.
- [Reporte de validación](implementation-report.md).
- [Diagramas editables](../../diagramas/README.md): siete `.excalidraw`, derivados de todas las FKs reales.
- `schema-catalog.json`: catálogo extraído de PostgreSQL, incluidas las tablas opcionales de vector. Regenerarlo con `vector.py` y ejecutar `render_docs.py` tras cambiar el esquema.
