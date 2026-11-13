# Continuidad: base de datos

**Fecha:** 13 de noviembre de 2026. **Rama:** `feature/database-foundation`, creada desde `develop`.

El trabajo se guarda por petición del usuario antes del cierre definitivo. No se ha integrado a develop ni publicado. Convención vigente: `tipo(alcance): emoji descripción`, por ejemplo `feat(database): ✨ agrega esquema inicial`.

## Lo implementado

- V1 permanece intacta. V2–V7 implementan el núcleo: identidad/RBAC, integraciones, repositorios, trabajos, scans/SBOM, componentes/versiones/apariciones, grafo, vulnerabilidades, comparación, reportes y auditoría.
- V101–V104 separan documentación, citas/modelos, impacto, notificaciones y programación. V201 añade embeddings opcionales con validación de dimensión por modelo.
- 76 tablas core; 115 con fase future; 116 al activar vector. Recuentos sin `flyway_schema_history`.
- FKs compuestas para tenant, scan y repositorio; índices, UNIQUE, CHECK, semillas de referencia y transiciones de procesos. Auditoría append-only.
- RLS con FORCE, rol runtime no propietario sin BYPASSRLS/DDL y rol migrador independiente. `TenantTransactions` establece contexto transaccional mediante parámetro JDBC.
- Bootstrap de roles apto para volúmenes existentes. Compose sigue local y no publica PostgreSQL. El `.env` local usa `SPRING_PROFILES_ACTIVE=future`, sin pgvector en la base de trabajo; secretos no versionados.
- Diccionario y catálogo real en `docs/database/`; normalización documentada sin afirmar una demostración universal de 5NF.
- Siete diagramas nativos editables en `diagramas/`, derivados del catálogo real.

## Validaciones completadas

- Build backend con Java 21 en Docker: correcto; 2 pruebas JUnit, sin fallos.
- Actualización del volumen original desde V1 al núcleo y después a future: arrancó correctamente.
- Suite SQL del esquema local: **41 comprobaciones** satisfactorias.
- Instalación completa desde cero en base temporal: **41 comprobaciones** satisfactorias; la base temporal se eliminó.
- Pila temporal con pgvector: **47 comprobaciones** satisfactorias, incluida dimensión, duplicados, modelos distintos y cruce de tenants; la pila temporal se eliminó.
- Fixtures transaccionales con rollback. Las pruebas cambian realmente a `dependency_app` para comprobar RLS, no se limitan a ejecutar como superusuario.
- Validación estructural de 7 archivos Excalidraw: 116 tablas, 246 FKs, IDs y bindings. No se abrió la aplicación Excalidraw para revisar presentación visual.

Los logs de esta sesión están en `/tmp/di-database-build.log`, `/tmp/di-db-final-tests.log`, `/tmp/di-db-fresh-final.log` y `/tmp/di-db-vector-final.log`. Son temporales; la suite está versionada y permite reproducirlos.

## Pendiente inmediato al retomar

1. Revisar el diff y las migraciones junto con la especificación `docs/DATABASE_DEPENDENCY_INTELLIGENCE.md`. No modificar migraciones aplicadas: cualquier corrección requiere una versión nueva.
2. Completar la comprobación final de `/health` a través de Nginx tras recrear el backend. La última comprobación global de salud, historial Flyway, roles y ausencia de fixtures fue interrumpida antes de obtener resultado; no darla por hecha. Si Nginx conserva una dirección antigua del backend, recrear frontend y volver a probar.
3. Confirmar en el catálogo local 115 tablas, historial Flyway exitoso, roles sin superusuario/BYPASSRLS y ausencia de extensión vector en la base habitual. Confirmar también que no quedan fixtures ni recursos temporales de validación.
4. Revisar visualmente los `.excalidraw` en un editor compatible: corregir rutas/superposiciones si hace falta sin perder FKs. No existe validación visual ni exportación SVG/PNG comprobada.
5. Revisar la integración real de `TenantTransactions` con el pool/JPA mediante una prueba de aplicación. La expiración del contexto se probó con SQL en la misma conexión, pero aún no con un caso de uso Java y Hikari/JPA extremo a extremo.
6. Cerrar el reporte de implementación y revisar criterios de aceptación. No considerar terminada la funcionalidad del producto por tener el DDL.

## Siguiente paso funcional

Implementar identidad, membresía y autorización antes de exponer datos. La API sigue denegando endpoints de negocio. El tenant debe proceder de una membresía validada, nunca de un UUID o header confiado directamente. RLS no sustituye autorización por usuario/recurso; un login SQL puede establecer su contexto.

Añadir entidades/adaptadores por agregado cuando exista el caso de uso, sin generar mappings JPA vacíos de todas las tablas. Después, construir el flujo repositorio → job/scan → SBOM → inventario → OSV. La escritura de advisories globales necesitará una identidad de ingesta con permisos específicos: runtime solo los lee; no usar credenciales de administrador en la API.

Faltan workers, limpieza real de archivos temporales, matching determinista por ecosistema, proveedores de autenticación/IA y decisiones de retención. La expiración almacenada no elimina archivos. No hay scanner, RAG ni envío de notificaciones funcionales por tener tablas.

## Comandos para retomar

Desde la raíz, con Docker local disponible:

```bash
git switch feature/database-foundation
docker compose up --build -d
bash tools/database/verify.sh
python3 tools/database/fresh.py
python3 tools/database/vector.py
python3 tools/database/render_docs.py
docker compose exec -T frontend wget -qO- http://localhost/health
```

No hace falta repetir todas las pruebas si solo se está leyendo documentación; ejecutarlas tras cambios relevantes o para confirmar estado perdido. `fresh.py` usa una base temporal independiente; `vector.py` usa una pila temporal sin montar el volumen habitual. No ejecutar contra infraestructura ajena al Compose del proyecto.
