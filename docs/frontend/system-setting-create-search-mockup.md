# Mockup: búsqueda y gestión de configuraciones del sistema

Pantalla de administración para consultar, crear y modificar el catálogo
aprobado de configuraciones del sistema. Solo está disponible para
administradores.

La creación añade una definición al catálogo; no permite crear claves
temporales o eliminar configuraciones que estén en uso. El catálogo define la
identidad, el tipo, el valor predeterminado y las restricciones de cada opción.
La pantalla ofrece las operaciones de lectura, búsqueda, creación, edición y
restauración del valor predeterminado.

```text
╔══════════════════════════════════════════════════════════════════════════════╗
║                                                                              ║
║  ADMINISTRACIÓN                                                             ║
║  Configuraciones del sistema                              [Nueva configuración]║
║  Gestiona las preferencias generales de la aplicación.                     ║
║                                                                              ║
╠══════════════════════════════════════════════════════════════════════════════╣
║                                                                              ║
║  Cadena de búsqueda                                                         ║
║  ┌──────────────────────────────────────────────────────────────────────┐   ║
║  │ 🔍 Buscar por nombre, clave o descripción                            │   ║
║  └──────────────────────────────────────────────────────────────────────┘   ║
║                                                                              ║
║  Categoría                         Estado                                  ║
║  ┌──────────────────────────────┐  ◉ Todas                                 ║
║  │ Todas las categorías       ▾ │  ○ Modificadas                           ║
║  └──────────────────────────────┘  ○ Valores predeterminados               ║
║                                                                              ║
║                         [Buscar]  [Limpiar]                                ║
║                                                                              ║
║  10 configuraciones · 3 modificadas                         [Guardar todo] ║
║                                                                              ║
╠══════════════════════════════════════════════════════════════════════════════╣
║  INTERFAZ                                                                  ║
║                                                                              ║
║  ┌────────────────────────────────────────────────────────────────────────┐ ║
║  │ Tema                                             ui.theme              │ ║
║  │ Tema visual predeterminado de la aplicación                            │ ║
║  │                                                                        │ ║
║  │ Valor                                                                  │ ║
║  │ ┌────────────────────────────────────────────────────────────────────┐ │ ║
║  │ │ Claro                                                           ▾ │ │ ║
║  │ └────────────────────────────────────────────────────────────────────┘ │ ║
║  │ Permitidos: claro · oscuro · sistema                                   │ ║
║  │ Predeterminado: claro · Versión: 2                                     │ ║
║  │                                                     [Editar] [Restaurar]║ ║
║  └────────────────────────────────────────────────────────────────────────┘ ║
║                                                                              ║
║  ┌────────────────────────────────────────────────────────────────────────┐ ║
║  │ Modo compacto                                ui.compactMode             │ ║
║  │ Usa un espaciado compacto en la aplicación                             │ ║
║  │                                                                        │ ║
║  │ Valor                                      [● Activado] [○ Desactivado] │ ║
║  │ Predeterminado: desactivado · Versión: 0                               │ ║
║  │                                                     [Editar] [Restaurar]║ ║
║  └────────────────────────────────────────────────────────────────────────┘ ║
║                                                                              ║
╠══════════════════════════════════════════════════════════════════════════════╣
║  NOTIFICACIONES                                                            ║
║                                                                              ║
║  ┌────────────────────────────────────────────────────────────────────────┐ ║
║  │ Notificaciones por correo                 notifications.emailEnabled   │ ║
║  │ Activa las notificaciones por correo de la aplicación                   │ ║
║  │ Valor: [● Activado] [○ Desactivado]                                    │ ║
║  │ Predeterminado: activado · Versión: 1                   [Editar] [Rest.]║ ║
║  └────────────────────────────────────────────────────────────────────────┘ ║
║                                                                              ║
║  ┌────────────────────────────────────────────────────────────────────────┐ ║
║  │ Notificaciones dentro de la aplicación  notifications.inAppEnabled     │ ║
║  │ Activa las notificaciones mostradas dentro de la aplicación             │ ║
║  │ Valor: [● Activado] [○ Desactivado]                                    │ ║
║  │ Predeterminado: activado · Versión: 0                   [Editar] [Rest.]║ ║
║  └────────────────────────────────────────────────────────────────────────┘ ║
║                                                                              ║
╠══════════════════════════════════════════════════════════════════════════════╣
║  IMPORTACIÓN                                                               ║
║                                                                              ║
║  ┌────────────────────────────────────────────────────────────────────────┐ ║
║  │ Tamaño máximo del lote                         import.maxBatchSize      │ ║
║  │ Máximo de registros aceptados en un lote de importación                │ ║
║  │                                                                        │ ║
║  │ Valor                                                                  │ ║
║  │ ┌────────────────────────────────────────────────────────────────────┐ │ ║
║  │ │ 1000                                                             │ │ ║
║  │ └────────────────────────────────────────────────────────────────────┘ │ ║
║  │ Rango permitido: 1–10000 · Predeterminado: 1000 · Versión: 0           ║
║  │                                                     [Editar] [Restaurar]║ ║
║  └────────────────────────────────────────────────────────────────────────┘ ║
║                                                                              ║
║  … Más configuraciones según la categoría seleccionada …                  ║
║                                                                              ║
╠══════════════════════════════════════════════════════════════════════════════╣
║  Cambios pendientes: 3                                                      ║
║                         [Previsualizar] [Cancelar] [Guardar todo]           ║
║                                                                              ║
║  Copias de seguridad                                                        ║
║                         [Descargar copia] [Restaurar copia]                 ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

## Flujo de creación

Al pulsar `Nueva configuración`, el administrador completa una definición
antes de incorporarla al catálogo:

```text
╔══════════════════════════════════════════════════════════════════════════════╗
║  Nueva configuración del sistema                                             ║
║                                                                              ║
║  Clave única *                         Categoría *                           ║
║  ┌──────────────────────────────┐      ┌──────────────────────────────┐     ║
║  │ notifications.digestInterval│      │ Notificaciones             ▾ │     ║
║  └──────────────────────────────┘      └──────────────────────────────┘     ║
║  Usa minúsculas, puntos y nombres descriptivos.                              ║
║                                                                              ║
║  Etiqueta *                             Tipo *                               ║
║  ┌──────────────────────────────┐      ┌──────────────────────────────┐     ║
║  │ Tiempo de sesión             │      │ Entero                     ▾ │     ║
║  └──────────────────────────────┘      └──────────────────────────────┘     ║
║                                                                              ║
║  Descripción *                                                               ║
║  ┌──────────────────────────────────────────────────────────────────────┐   ║
║  │ Tiempo máximo de inactividad antes de cerrar la sesión                 │   ║
║  └──────────────────────────────────────────────────────────────────────┘   ║
║                                                                              ║
║  Valor predeterminado *                 Valor actual *                      ║
║  ┌──────────────────────────────┐        ┌──────────────────────────────┐   ║
║  │ 30                           │        │ 30                           │   ║
║  └──────────────────────────────┘        └──────────────────────────────┘   ║
║                                                                              ║
║  Límites (opcional)                                                         ║
║  Mínimo ┌──────────────┐   Máximo ┌──────────────┐                           ║
║         │ 1            │          │ 1440         │                           ║
║         └──────────────┘          └──────────────┘                           ║
║                                                                              ║
║  [Cancelar]  [Validar definición]  [Crear configuración]                   ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### Pasos

1. `Nueva configuración` abre el formulario y genera un borrador local; no
   cambia el catálogo.
2. El formulario adapta los campos al tipo seleccionado. Los textos pueden
   declarar valores permitidos, los booleanos usan un interruptor y los
   enteros admiten límites mínimo y máximo.
3. `Validar definición` comprueba que la clave sea única, que siga el formato
   permitido, que todos los campos obligatorios estén completos y que los
   valores sean compatibles con el tipo y sus restricciones.
4. `Crear configuración` solo se habilita tras una validación correcta y
   muestra un resumen de confirmación con la clave, el tipo y los valores.
5. Tras confirmar, la nueva configuración aparece en la categoría
   correspondiente, con versión `0`, y queda disponible en la búsqueda. Si la
   clave ya existe o la creación falla, el formulario conserva los datos y
   muestra el error sin crear un registro parcial.

## Comportamiento

- La búsqueda filtra por clave, etiqueta y descripción. El filtro de categoría
  permite seleccionar las categorías disponibles en el catálogo, incluidas
  `UI`, `NOTIFICATIONS`, `IMPORT` y `DISPLAY`.
- `Nueva configuración` está disponible solo para administradores y crea una
  definición persistente del catálogo; no ofrece un borrado destructivo de
  configuraciones en uso.
- Cada configuración se edita con el control correspondiente a su tipo:
  selector para valores de texto restringidos, interruptor para booleanos y
  campo numérico para enteros con límites visibles.
- `Editar` muestra el control editable; `Restaurar` prepara el valor
  predeterminado como cambio pendiente. Ninguna acción persiste por sí sola.
- `Previsualizar` valida todos los cambios pendientes sin escribirlos.
  `Guardar todo` aplica la actualización de forma atómica y muestra los
  conflictos de versión para que el administrador pueda recargar y decidir.
- `Cancelar` descarta los cambios pendientes. Los errores de validación,
  ausencia de permisos, conflictos y errores del servidor se muestran junto a
  una acción clara para resolverlos.
- `Descargar copia` genera una copia versionada del catálogo actual.
  `Restaurar copia` exige seleccionar una copia válida y confirmar la operación
  antes de reemplazar los valores.
- La pantalla debe tener estados explícitos de carga, resultados vacíos,
  acceso no autorizado, prohibición de acceso y error del servidor.

## Catálogo inicial

| Categoría | Clave | Tipo | Valor predeterminado |
|-----------|-------|------|----------------------|
| UI | `ui.theme` | Texto (`light`, `dark`, `system`) | `light` |
| UI | `ui.compactMode` | Booleano | `false` |
| Notificaciones | `notifications.emailEnabled` | Booleano | `true` |
| Notificaciones | `notifications.inAppEnabled` | Booleano | `true` |
| Notificaciones | `notifications.importCompleted` | Booleano | `true` |
| Importación | `import.autoValidate` | Booleano | `true` |
| Importación | `import.preserveHistory` | Booleano | `true` |
| Importación | `import.maxBatchSize` | Entero (1–10000) | `1000` |
| Visualización | `display.maxSearchResults` | Entero (10–100) | `50` |
| Visualización | `display.maxPageSize` | Entero (10–100) | `50` |

## Leyenda de componentes

| Símbolo | Significado |
|---------|-------------|
| `[Editar]` | Activa la edición de una configuración |
| `[Nueva configuración]` | Abre el formulario de alta de una configuración |
| `[Validar definición]` | Comprueba la nueva definición sin persistirla |
| `[Crear configuración]` | Confirma el alta después de validar la definición |
| `[Restaurar]` | Restablece el valor predeterminado como cambio pendiente |
| `[● Activado] [○ Desactivado]` | Control booleano |
| `[Previsualizar]` | Valida cambios sin persistirlos |
| `[Guardar todo]` | Persiste los cambios pendientes |
| `[Descargar copia] [Restaurar copia]` | Exportación y restauración versionadas |
