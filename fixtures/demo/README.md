# Escenario de presentación

Estos archivos se han construido manualmente para la demo. **Son datos sintéticos**, no resultados de un scanner ni pruebas sobre un repositorio real. No tienen vulnerabilidades verificadas, commit analizado ni relaciones directas/transitivas inferidas.

Contrato inicial: CycloneDX JSON 1.6, ecosistema Maven y un único proyecto de ejemplo. Referencia de formato: [documentación oficial CycloneDX 1.6](https://cyclonedx.org/docs/1.6/json/). El soporte del importador se limitará al subconjunto documentado; no implica validación completa del estándar.

| Archivo | Uso | Resultado esperado cuando exista el importador |
| --- | --- | --- |
| inventory-demo.cdx.json | Recorrido válido | Un snapshot con tres ocurrencias de biblioteca |
| duplicate-bom-ref.cdx.json | Ejemplo de rechazo | Error por referencias duplicadas y ninguna escritura parcial |

La aplicación de `metadata.component` identifica el proyecto raíz; no cuenta entre las tres bibliotecas del inventario de este ejemplo.

| bom-ref | Nombre | Versión | PURL |
| --- | --- | --- | --- |
| demo-core | demo-core | 1.0.0 | pkg:maven/org.example/demo-core@1.0.0 |
| demo-utils | demo-utils | 2.0.0 | pkg:maven/org.example/demo-utils@2.0.0 |
| demo-client | demo-client | 1.1.0 | pkg:maven/org.example/demo-client@1.1.0 |

Las tres ocurrencias se clasificarán como `UNKNOWN`: el archivo no demuestra si son dependencias directas o transitivas. Cada importación aceptada generará su propio identificador de snapshot; `serialNumber` es procedencia del archivo, no el ID de la fila en la base.

El archivo de rechazo es JSON sintácticamente válido y deliberadamente inválido para el contrato del importador. El parser ya está implementado y sus pruebas unitarias cubren este tipo de referencias repetidas; todavía no hay carga HTTP ni persistencia conectadas. Este bloque no ejecutó los archivos de esta carpeta contra un flujo de importación completo.

Ver [arranque local](../../docs/DEMO_LOCAL.md).
