# Grafo de dependencias

La vista muestra el último snapshot completado por repositorio y ecosistema. El backend expone nodos y aristas tenant-scoped; Angular los consolida por ecosistema, paquete y versión para construir un mapa dirigido por niveles.

## Interacción

- Proyecto a la izquierda; dependencias directas y transitivas en columnas según la distancia observada.
- Paquetes compartidos aparecen en una tarjeta con sus rutas y ámbitos agrupados.
- La búsqueda resalta las coincidencias y su camino hacia el proyecto. La lista inferior conserva navegación accesible y permite enfocar nodos que no estén en el recorte inicial.
- Tema claro/oscuro toma los tokens de `frontend/src/theme/_tokens.scss`.

## Rendimiento y límites

Se reutiliza Cytoscape.js ya instalado. El layout se posiciona con BFS O(V+E); el canvas renderiza como máximo 1.000 nodos por vez y prioriza búsquedas/selecciones grandes. El listado conserva el inventario completo en tandas de 100. El grafo representa únicamente relaciones respaldadas por los scans actuales: npm usa `package-lock` v2/v3; Maven sigue limitado a declaraciones directas de POM.
