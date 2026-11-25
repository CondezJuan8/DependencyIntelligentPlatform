# Dependency Intelligence — Spectrum Ledger

## Identidad

Spectrum Ledger presenta el recorrido de evidencia desde el repositorio hasta una decisión revisable. Su gramática combina superficies de grafito, tipografía compacta y registros alineados; un espectro de índigo a cian marca selección, actividad confirmada y conexiones entre etapas. La identidad debe sentirse técnica y precisa sin convertir las pantallas de trabajo en una consola decorativa.

Las referencias del brief se usaron como inspiración para contraste, densidad y acentos locales. No se copiaron layouts, marcas ni componentes. La navegación y cada módulo conservan el flujo real del producto.

## Color y temas

Los tokens viven en `frontend/src/theme/_tokens.scss` y `frontend/src/theme/_appearance.scss`. El modo oscuro es inicial; el selector existente conserva preferencia del usuario, de la organización y del sistema.

| Rol | Claro | Oscuro | Uso |
| --- | --- | --- | --- |
| Lienzo | `#F4F5F8` | `#0C1018` | Área general de trabajo |
| Superficie | `#FFFFFF` | `#131925` | Contenido, tablas y formularios |
| Superficie elevada | `#FFFFFF` | `#252F40` | Popovers, diálogos y acciones flotantes |
| Navegación | `#10131B` | `#0A0D13` | Sidebar y marca |
| Texto | `#171C29` | `#F1F3F8` | Contenido principal |
| Acción | `#5148E5` | `#AAA4FF` | Acción primaria y selección |
| Señal | `#087F99` | `#48C8DC` | Actividad confirmada, Nexo y procedencia |
| Éxito | `#13835B` | `#5ED5A1` | Estado correcto; siempre acompañado de etiqueta |
| Precaución | `#A96712` | `#F1BB68` | Cobertura parcial o estado por revisar |
| Riesgo alto | `#CF573C` | `#FF9A77` | Hallazgo de riesgo alto |
| Riesgo crítico | `#C52F50` | `#FF6D88` | Hallazgo crítico |

El degradado índigo–azul–cian se reserva a marcas estrechas de navegación y al gráfico de acceso. No se usa en texto ni como fondo general. Las categorías semánticas llevan también nombre o explicación; OSV y el scanner siguen siendo fuentes distintas.

## Tipografía y forma

IBM Plex Sans local es la familia de interfaz. Cifras usan numerales tabulares; código y versiones usan la pila monoespaciada del sistema. Los títulos pesan más por escala y composición que por efectos.

- Ritmo de 4 px, con gutters de 24–32 px en escritorio y 16 px en móvil.
- Controles: 4 px de radio; superficies: casi rectas; diálogo: hasta 8 px.
- Sombras solo en menús y capas que se separan del contenido.
- Iconos: set SVG local `DiIcon`, siempre acompañados de texto salvo que tengan nombre accesible.

## Composición y navegación

El shell organiza los destinos por intención y conserva sidebar estable, contexto de ruta y acciones globales en la barra superior. En móvil, la navegación pasa a drawer. La opción actual permanece identificable y los módulos no implementados siguen marcados **En preparación**.

Las pantallas usan la estructura propia del trabajo:

- **Dashboard:** señales OSV guardadas, repositorios y registro de análisis, sin métricas ficticias.
- **Repositorios:** ingreso de GitHub/local y registro de fuentes accesibles.
- **Análisis:** inicio manual, estados reales e historial trazable.
- **Inventario:** catálogo, filtros y procedencia por snapshot.
- **Vulnerabilidades:** aviso OSV, paquete afectado y evidencia relacionada.
- **Documentación:** carga, biblioteca, búsqueda y asociación a fuentes autorizadas.
- **Nexo:** conversación, citas y evidencia recuperada.
- **Reportes:** alcance, vista previa y exportación.
- **Equipo y administración:** roles, membresías y permisos por repositorio.

Las superficies agrupan un área de trabajo completa; evitar paneles anidados y controles visuales sin acción. Tablas y listas mantienen el orden de lectura en móvil, con desplazamiento contenido solo cuando la estructura realmente lo necesita.

## Interacción y movimiento

Transiciones de color y posición duran 160–240 ms. La entrada principal del acceso y el énfasis breve del dashboard son momentos puntuales. Las filas no se animan repetidamente. El cian pulsante y el espectro en movimiento quedan reservados a operaciones que el producto marque activas; no se inventa progreso.

Todo estado conserva su representación final si `prefers-reduced-motion` está activo. Foco de teclado visible, estados de hover/pulsado/deshabilitado y confirmación legible son parte del sistema.

## Accesibilidad y adaptación

Revisar 375, 390, 768, 1024 y 1440 px. Reorganizar antes de reducir; no permitir overflow horizontal del documento. Mantener controles táctiles de 44 px cuando sea posible, labels persistentes, landmarks semánticos, mensajes de error/estado anunciados, zoom, pegado y autocompletado en acceso. El contraste de texto debe cumplir WCAG 2.2 AA en los dos temas.

## Restricciones de producto

No cambiar Angular standalone, Material, CDK, TypeScript, RxJS o Sass por razones visuales. No tocar API, autenticación, permisos, backend o datos. No inventar hallazgos, porcentajes, tendencias ni conexiones. Mantener las acciones existentes y validar por separado las rutas autenticadas cuando haya datos autorizados.

## Validación

El build del frontend valida compilación, no recorridos autenticados. La base local Compose está vacía; la inspección visual de filtros, tablas e interacciones con datos reales sigue dependiendo del bootstrap seguro y de registros autorizados. No declarar validado lo que no se pudo abrir en contexto autenticado.
