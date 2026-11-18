"""Generate data dictionary and native Excalidraw/SVG diagrams from a PostgreSQL catalog export.

No SQL is parsed or invented here; all entity attributes, keys, policies and ER connectors
come from export-schema.sql applied to the real migrated database. Standard library only;
Pillow, when already available, adds PNG previews. Never connects to providers.
"""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import math
import textwrap
from collections import defaultdict
from pathlib import Path


PURPOSES = {
    'organization_settings': ('identity', 'Shared organization workspace theme and language, restricted to active members and managed by organization owners/admins.'),
    'member_repository_permissions': ('identity', 'Direct permission grants for a membership and repository, intersected with its effective role.'),
    'organizations': ('identity', 'Organización propietaria y límite de tenant.'),
    'users': ('identity', 'Identidad global con verificación de correo y versión de credenciales para revocar sesiones.'),
    'user_identities': ('identity', 'Vínculos verificados a identidades externas Google/GitHub y sujetos históricos.'),
    'user_password_credentials': ('identity', 'Hash de contraseña local, separado de identidades externas.'),
    'user_access_tokens': ('identity', 'Hashes de tokens de verificación, recuperación, sesión y decisiones de admisión, de un solo uso y con expiración.'),
    'organization_memberships': ('identity', 'Participación de un usuario en una organización; una por par.'),
    'roles': ('identity', 'Roles globales base; no configura roles personalizados por inferencia.'),
    'permissions': ('identity', 'Permisos atómicos de aplicación.'),
    'role_permissions': ('identity', 'Asignación independiente M:N de permisos a roles.'),
    'membership_roles': ('identity', 'Varios roles por membresía sin arrays ni CSV.'),
    'membership_invitations': ('identity', 'Invitación con token hash, estado y expiración.'),
    'invitation_roles': ('identity', 'Roles propuestos para cada invitación.'),
    'organization_teams': ('identity', 'Equipos de una organización.'),
    'team_memberships': ('identity', 'Membresías pertenecientes a equipos del mismo tenant.'),
    'repository_team_access': ('identity', 'Permisos por equipo y repositorio del mismo tenant.'),
    'member_repository_assignments': ('identity', 'Permisos directos de una membresía en un repositorio, delimitados por organización.'),
    'platform_administrators': ('identity', 'Asignación explícita de cuentas con privilegios administrativos globales de plataforma.'),
    'integration_providers': ('integration', 'Códigos de proveedor; un catálogo no implementa la integración.'),
    'integrations': ('integration', 'Conexión de una organización a una cuenta externa, sin secretos en claro.'),
    'github_installations': ('integration', 'Subtipo GitHub: instalación y cuenta conectadas a una integración GitHub.'),
    'repositories': ('integration', 'Repositorio identificado por proveedor e ID externo dentro del tenant.'),
    'repository_branches': ('integration', 'Ramas observadas y commit más reciente, si existe.'),
    'repository_commits': ('integration', 'Identidad de commit dentro de un repositorio; no es un catálogo global de SHA.'),
    'webhook_events': ('integration', 'Recepciones idempotentes por proveedor/delivery; firma se verifica en el adaptador futuro.'),
    'integration_sync_runs': ('integration', 'Historial y estado de sincronización.'),
    'repository_settings': ('integration', 'Configuración escalar de análisis por repositorio.'),
    'package_ecosystems': ('component', 'Ecosistemas conocidos; solo Maven/npm están confirmados como cobertura objetivo.'),
    'dependency_scopes': ('component', 'Scopes conocidos de dependencias.'),
    'dependency_relationship_types': ('component', 'Tipos semánticos de aristas.'),
    'components': ('component', 'Identidad lógica del paquete, privada por organización.'),
    'component_versions': ('component', 'Versión exacta textual; no asume SemVer universal.'),
    'component_identifiers': ('component', 'Identificadores alternativos de una versión, incluidos PURL con distintos qualifiers.'),
    'component_hashes': ('component', 'Hashes observados por algoritmo y versión.'),
    'license_catalog': ('component', 'Licencias referenciables por código, sin sembrar detecciones ficticias.'),
    'component_licenses': ('component', 'Licencias declaradas a nivel de versión de paquete.'),
    'scan_component_licenses': ('component', 'Licencias observadas en una aparición concreta del SBOM.'),
    'component_external_references': ('component', 'Referencias externas de versión, normalizadas como filas.'),
    'job_types': ('scan', 'Tipos de trabajos tenant-scoped soportados por el modelo.'),
    'job_statuses': ('scan', 'Estados de procesamiento; no ejecuta workers por sí solo.'),
    'background_jobs': ('scan', 'Cola persistente por organización, locks, deduplicación y límite de intentos.'),
    'background_job_attempts': ('scan', 'Intentos numerados limitados por la configuración del job.'),
    'background_job_events': ('scan', 'Eventos de ejecución y metadatos técnicos sanitizados por la aplicación.'),
    'scanner_tools': ('scan', 'Herramientas de scanner conocidas; no instala ni ejecuta scanners.'),
    'scan_profiles': ('scan', 'Configuración reutilizable de duración y ecosistemas.'),
    'scan_profile_ecosystems': ('scan', 'Ecosistemas habilitados por perfil como relación M:N.'),
    'scans': ('scan', 'Snapshot con origen, estado, repositorio y commit cuando existe; importación no inventa scanner.'),
    'scan_stages': ('scan', 'Etapas ordenadas del pipeline.'),
    'scan_stage_executions': ('scan', 'Historial de ejecución de una etapa e intento.'),
    'scan_logs': ('scan', 'Diagnóstico estructurado; la redacción de secretos corresponde al escritor.'),
    'scan_artifacts': ('scan', 'Referencias privadas a artefactos realmente almacenados con hash/tamaño.'),
    'sbom_documents': ('scan', 'Procedencia de SBOM, antes sbom_imports; archivo retenido opcional, nunca inventado.'),
    'sbom_external_references': ('scan', 'Referencias externas asociadas al documento SBOM.'),
    'scan_components': ('scan', 'Aparición de una versión dentro de un snapshot, con bom_ref local.'),
    'dependency_edges': ('scan', 'Arista entre dos apariciones del mismo snapshot; no demuestra comunicación runtime.'),
    'scan_comparisons': ('scan', 'Comparación reproducible entre snapshots distintos del mismo repositorio.'),
    'scan_component_changes': ('scan', 'Resultado derivado/persistido de comparación por paquete y versiones.'),
    'vulnerabilities': ('vulnerability', 'Advisory público canónico por fuente/identificador.'),
    'vulnerability_sources': ('vulnerability', 'Fuentes públicas de vulnerabilidades.'),
    'severity_levels': ('vulnerability', 'Severidades normalizadas y orden; UNKNOWN conserva incertidumbre.'),
    'vulnerability_aliases': ('vulnerability', 'Aliases independientes como CVE o GHSA por advisory.'),
    'vulnerability_references': ('vulnerability', 'Referencias/URLs de advisories como filas separadas.'),
    'vulnerability_severities': ('vulnerability', 'Evaluaciones por fuente, método, fecha, score/vector y versión CVSS.'),
    'vulnerability_source_records': ('vulnerability', 'Payload fuente y hash con base RAW_BYTES o CANONICAL_JSON explícita.'),
    'vulnerability_affected_packages': ('vulnerability', 'Paquetes públicos afectados según advisory, separados del catálogo privado.'),
    'vulnerability_affected_ranges': ('vulnerability', 'Rangos de versión por paquete afectado y tipo.'),
    'vulnerability_range_events': ('vulnerability', 'Eventos ordenados de introducción/fix/límites del rango.'),
    'vulnerability_fixed_versions': ('vulnerability', 'Versiones corregidas publicadas por la fuente para un paquete afectado.'),
    'scan_vulnerability_findings': ('vulnerability', 'Hallazgo de advisory en una aparición de un snapshot.'),
    'finding_fixed_versions': ('vulnerability', 'Fixes observados en el contexto de un hallazgo; no se promueven a verdad global.'),
    'finding_evidence': ('vulnerability', 'Explicación y registro fuente del mismo advisory que el hallazgo.'),
    'document_types': ('documentation', 'Tipos documentales conocidos.'),
    'documents': ('documentation', 'Documento lógico y política de retención de la organización.'),
    'document_versions': ('documentation', 'Original privado retenido o eliminado, hash, MIME/tamaño y temporal con expiración.'),
    'document_repositories': ('documentation', 'Asociación M:N documento/repositorio del mismo tenant.'),
    'document_components': ('documentation', 'Asociación M:N documento/paquete.'),
    'document_component_versions': ('documentation', 'Asociación M:N documento/versión concreta.'),
    'document_chunks': ('documentation', 'Texto extraído con orden, páginas/sección y hash; índice textual simple.'),
    'document_processing_runs': ('documentation', 'Extracción/OCR/chunking/embedding/indexación con herramienta/versión.'),
    'document_processing_artifacts': ('documentation', 'Artefactos temporales del procesamiento y su expiración.'),
    'assistant_model_providers': ('documentation', 'Proveedores confirmados Gemini/Ollama, sin claves ni modelos sembrados.'),
    'assistant_model_versions': ('documentation', 'Modelo/revisión de generación configurado, sin presumir disponibilidad o gratuidad.'),
    'embedding_models': ('documentation', 'Modelo/revisión/dimensión verificados antes de usar vectores.'),
    'document_chunk_embeddings': ('documentation', 'Metadatos de chunk/modelo; vector físico requiere migración opcional.'),
    'assistant_prompt_versions': ('documentation', 'Instrucciones versionadas para reproducibilidad, sin secretos.'),
    'assistant_conversations': ('documentation', 'Conversación de una membresía dentro de una organización.'),
    'assistant_messages': ('documentation', 'Mensajes ordenados, modelo/prompt y consumo observado.'),
    'assistant_message_evidences': ('documentation', 'Citas tipadas con FK real a chunk, aparición o hallazgo autorizado.'),
    'impact_analyses': ('impact', 'Solicitud ADD/UPDATE/REMOVE/REPLACE ligada a snapshot/commit y versiones.'),
    'impact_findings': ('impact', 'Clasificación KNOWN/POTENTIAL/VALIDATED; VALIDATED exige prueba terminada del mismo análisis.'),
    'impact_finding_repositories': ('impact', 'Repositorios relacionados con hallazgos, con FK real.'),
    'impact_finding_component_versions': ('impact', 'Versiones relacionadas con hallazgos.'),
    'impact_finding_documents': ('impact', 'Documentos relacionados con hallazgos.'),
    'impact_finding_vulnerabilities': ('impact', 'Advisories públicos relacionados con hallazgos tenant-scoped.'),
    'impact_evidences': ('impact', 'Evidencia observada con origen/extracto/página/fecha/hash.'),
    'impact_finding_evidences': ('impact', 'Asociación hallazgo/evidencia del mismo análisis.'),
    'impact_recommendations': ('impact', 'Acción, prioridad y fundamento del análisis.'),
    'impact_recommendation_evidences': ('impact', 'Relación M:N recomendación/evidencia del mismo análisis.'),
    'impact_test_suggestions': ('impact', 'Pruebas sugeridas; no implica que hayan sido ejecutadas.'),
    'version_change_records': ('impact', 'Cambios documentados con fuente, versión y fecha de consulta.'),
    'change_record_evidences': ('impact', 'Relación M:N cambio/evidencia.'),
    'document_review_flags': ('impact', 'Documentos potencialmente desactualizados; no afirma que sean incorrectos.'),
    'change_simulations': ('impact', 'Solicitud aislada ligada a un commit real del análisis y configuración inmutable.'),
    'simulation_test_runs': ('impact', 'Resultados de pruebas; evidencia terminada inmutable, sin ejecutar comandos desde SQL.'),
    'change_pull_request_proposals': ('impact', 'Propuesta de PR; publicación requiere aprobación y referencia externa.'),
    'report_types': ('reporting', 'Tipos de informe.'),
    'report_formats': ('reporting', 'Formatos de exportación; catálogo no significa exportador implementado.'),
    'report_definitions': ('reporting', 'Definiciones de informes.'),
    'report_jobs': ('reporting', 'Solicitud de generación, formato, scope separado y parámetros técnicos.'),
    'report_repositories': ('reporting', 'Repositorios seleccionados para un informe sin arrays.'),
    'report_scans': ('reporting', 'Snapshots seleccionados para un informe sin arrays.'),
    'report_artifacts': ('reporting', 'Archivo generado privado, hash, tamaño, MIME y expiración.'),
    'report_schedules': ('reporting', 'Programación futura por zona horaria, deshabilitada por defecto.'),
    'report_schedule_recipients': ('reporting', 'Un destinatario por fila: membresía o correo externo, no ambos.'),
    'notification_channels': ('notifications', 'Canales conocidos; solo in-app está priorizado inicialmente.'),
    'notification_rules': ('notifications', 'Reglas de eventos por organización.'),
    'notification_subscriptions': ('notifications', 'Suscripción independiente regla/membresía/canal.'),
    'notifications': ('notifications', 'Evento lógico deduplicado con referencias tipadas a sus recursos.'),
    'notification_deliveries': ('notifications', 'Entrega y lectura por membresía/canal, separada del evento.'),
    'notification_delivery_attempts': ('notifications', 'Intentos de entrega, resultado y referencia de proveedor sin secretos.'),
    'audit_events': ('audit', 'Eventos append-only; mantenimiento excepcional exige rol dedicado.'),
    'platform_audit_events': ('audit', 'Registro append-only de acciones administrativas globales con actor y objetivo.'),
    'audit_event_changes': ('audit', 'Cambios auditables separados, con exclusión básica de atributos secretos.'),
    'data_retention_policies': ('audit', 'Retención/eliminación por organización y tipo, sin inventar periodos legales.'),
    'data_deletion_requests': ('audit', 'Solicitud tipada de eliminación y estado; no borra historia en cascada.'),
}


def stable_id(text):
    return hashlib.sha256(text.encode()).hexdigest()[:20]


def write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8', newline='\n')


def dictionary(schema, output):
    lines = ['# Diccionario de datos del esquema aplicado', '',
             f"Generado desde `schema.json`, exportado de PostgreSQL tras Flyway V{schema['latest_migration']}. "
             'No contiene filas de negocio ni secretos. Los nombres, columnas, restricciones e índices son del catálogo real.', '',
             'PK/U: clave primaria/única, posiblemente compuesta. FK: referencia. NO ACTION/RESTRICT preservan historia; no se aplican cascadas indiscriminadas.', '',
             'Las tablas globales de identidad se acceden desde el backend de autenticación; no son catálogos públicos ni autorizan acceso a organizaciones. '
             'Los catálogos públicos de advisories no contienen apariciones privadas. Las tablas tenant-scoped usan RLS y FKs de organización.', '']
    for table in schema['tables']:
        name = table['name']
        module, purpose = PURPOSES[name]
        tenant = table['rls']
        keys = table['constraints']
        lines += [f'## {name}', '', purpose, '',
                  f"Módulo: **{module}**. Ámbito: **{'tenant' if tenant else 'global'}**. "
                  f"RLS: {table['rls']}; FORCE: {table['force_rls']}.", '',
                  '| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |',
                  '| --- | --- | --- | --- | --- |']
        for column in table['columns']:
            labels = sorted({{'p':'PK', 'u':'U', 'f':'FK'}[key['kind']] for key in keys
                             if key['kind'] in ('p','u','f') and column['name'] in (key['columns'] or [])})
            default = (column['default'] or '—').replace('|', '\\|').replace('\n', ' ')
            lines.append(f"| `{column['name']}` | `{column['type']}` | {'Sí' if column['nullable'] else 'No'} | {default} | {', '.join(labels)} |")
        lines += ['', 'Restricciones:', '']
        for key in keys:
            lines.append(f"- `{key['name']}`: `{key['definition']}`. Validada: {key['validated']}.")
        incoming = [(child['name'], key['columns']) for child in schema['tables'] for key in child['constraints']
                    if key['kind'] == 'f' and key['referenced_table'] == name]
        lines += ['', 'Referencias entrantes: ' + (', '.join(f"`{child}` ({', '.join(cols)})" for child, cols in incoming) or 'ninguna') + '.', '',
                  'Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:', '']
        for index in table['indexes']:
            lines.append(f"- `{index['definition']}`")
        lines += ['', 'Retención/borrado: ' + ('append-only; excepción administrativa explícita.' if name.startswith('audit_') else
                  'sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.' if tenant else
                  'catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.'), '']
        for policy in table['policies']:
            lines.append(f"- Política `{policy['name']}`, roles {', '.join(policy['roles'])}: USING `{policy['using']}`; CHECK `{policy['check']}`.")
        for trigger in table['triggers']:
            lines.append(f'- Trigger: `{trigger}`.')
        lines.append('')
    write(output / 'data-dictionary.md', '\n'.join(lines))


def element(kind, key, x, y, width, height, **extras):
    return dict(id=stable_id(key), type=kind, x=x, y=y, width=width, height=height, angle=0,
                strokeColor='#334155', backgroundColor='transparent', fillStyle='solid', strokeWidth=1,
                strokeStyle='solid', roughness=0, opacity=100, seed=int(stable_id(key)[:7],16),
                version=1, versionNonce=1, isDeleted=False, groupIds=[], frameId=None,
                roundness=None, boundElements=[], updated=0, link=None, locked=False, **extras)


def label(key, text, x, y, size=15, color='#1e293b'):
    lines = text.splitlines() or ['']
    value = element('text', key, x, y, max(map(len,lines))*size*.62, len(lines)*size*1.25,
                    fontSize=size, fontFamily=3, text=text, originalText=text, textAlign='left',
                    verticalAlign='top', containerId=None, autoResize=True, lineHeight=1.25)
    value['strokeColor'] = color
    return value


def draw_native(name, title, cards, links, destination, note):
    columns = 5 if len(cards)>35 else 4 if len(cards)>16 else 3
    width, gap, top = 440, 95, 240
    elements = [label(name+'-title', title, 0, 0, 24), label(name+'-note', note, 0, 42, 14),
                label(name+'-legend', 'PK / U = participa en clave (puede ser compuesta); FK = referencia; ? = nullable\nAzul: tenant/RLS · Gris: global · Blanco: referencia externa al módulo · conectores ER: 0..N → 1/0..1', 0, 72, 14)]
    boxes, row_bottoms = {}, {}
    x=0; y=top
    for offset in range(0,len(cards),columns):
        row=cards[offset:offset+columns]
        heights=[max(110, 56+len(card['lines'])*20) for card in row]
        bottom=y+max(heights)
        for col,(card,height) in enumerate(zip(row,heights)):
            x=col*(width+gap)
            box=element('rectangle', name+'-box-'+card['name'], x,y,width,height)
            box['backgroundColor'] = '#ffffff' if card.get('stub') else '#e7f2ff' if card.get('tenant') else '#f1f5f9'
            box['customData']={'table':card['name'], 'stub':card.get('stub',False), 'row':offset//columns}
            group=stable_id(name+'-group-'+card['name']); box['groupIds']=[group]
            heading=label(name+'-heading-'+card['name'],card['name'],x+12,y+10,16)
            heading['groupIds']=[group]
            body=label(name+'-body-'+card['name'],'\n'.join(card['lines']),x+12,y+39,14)
            body['groupIds']=[group]
            elements += [box,heading,body]
            boxes[card['name']]=box
        row_bottoms[offset//columns]=bottom
        y=bottom+155
    external_bus=columns*(width+gap)+25
    outgoing=defaultdict(int); slots=defaultdict(int)
    for link in links: outgoing[link['source']]+=1
    for index, link in enumerate(links):
        source,target=boxes[link['source']],boxes[link['target']]
        slots[link['source']]+=1
        sx=source['x']+source['width']; sy=source['y']+20+(source['height']-40)*slots[link['source']]/(outgoing[link['source']]+1)
        tx=target['x']+target['width']*.5; ty=target['y']
        source_row=source['customData']['row']; target_row=target['customData']['row']
        source_lane=row_bottoms[source_row]+25+(index%8)*12
        target_lane=target['y']-25-(index%8)*12
        gutter=sx+20+(index%5)*10
        bus=external_bus+(index%12)*12
        points=[[0,0],[gutter-sx,0],[gutter-sx,source_lane-sy],[bus-sx,source_lane-sy],
                [bus-sx,target_lane-sy],[tx-sx,target_lane-sy],[tx-sx,ty-sy]]
        arrow=element('arrow',name+'-link-'+str(index),sx,sy,
                      max(p[0] for p in points)-min(p[0] for p in points),
                      max(p[1] for p in points)-min(p[1] for p in points),points=points,
                      lastCommittedPoint=None,startBinding={'elementId':source['id'],'focus':0,'gap':2},
                      endBinding={'elementId':target['id'],'focus':0,'gap':2},startArrowhead=None,endArrowhead='arrow',elbowed=False)
        arrow['customData']={'constraints':link.get('constraints',[]),'logical':link.get('logical',False)}
        if link.get('logical'): arrow['strokeStyle']='dashed'
        source['boundElements'].append({'type':'arrow','id':arrow['id']}); target['boundElements'].append({'type':'arrow','id':arrow['id']})
        elements.append(arrow)
        elements.append(label(name+'-link-caption-'+str(index),link.get('label','0..N → 0..1' if link.get('optional') else '0..N → 1'),sx+5,sy-18,11,'#475569'))
    # Connectors are behind boxes/text; bindings remain native editable references.
    elements=sorted(elements,key=lambda item:0 if item['type']=='arrow' else 1)
    scene={'type':'excalidraw','version':2,'source':'https://excalidraw.com','elements':elements,
           'appState':{'viewBackgroundColor':'#ffffff','gridSize':None},'files':{}}
    validate(scene)
    write(destination/(name+'.excalidraw'),json.dumps(scene,ensure_ascii=False,indent=2)+'\n')
    export_previews(scene,destination/name)
    return scene


def validate(scene):
    assert scene['type']=='excalidraw' and scene['version']==2 and 'appState' in scene and 'files' in scene
    ids=[item['id'] for item in scene['elements']]; assert len(ids)==len(set(ids))
    for item in scene['elements']:
        for binding in ('startBinding','endBinding'):
            if item.get(binding): assert item[binding]['elementId'] in ids
        for bound in item.get('boundElements',[]): assert bound['id'] in ids


def export_previews(scene, stem):
    items=scene['elements']
    xs=[item['x']+max([p[0] for p in item.get('points',[[item['width'],0]])]) for item in items]
    ys=[item['y']+max([p[1] for p in item.get('points',[[0,item['height']]])]) for item in items]
    w=math.ceil(max(xs)+60); h=math.ceil(max(ys)+60)
    svg=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="-20 -20 {w} {h}">',
         '<rect x="-20" y="-20" width="100%" height="100%" fill="white"/>',
         '<defs><marker id="arrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto"><path d="M0,0 L7,3 L0,6" fill="none" stroke="#64748b"/></marker></defs>']
    try:
        from PIL import Image, ImageDraw, ImageFont
        scale=1
        canvas=Image.new('RGB',(math.ceil(w*scale),math.ceil(h*scale)),'white'); drawing=ImageDraw.Draw(canvas)
        font_path=next((candidate for candidate in ('C:/Windows/Fonts/consola.ttf',
            '/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf',
            '/usr/share/fonts/truetype/liberation2/LiberationMono-Regular.ttf') if Path(candidate).is_file()),None)
        def font(size):
            size=max(8,round(size*scale))
            return ImageFont.truetype(font_path,size) if font_path else ImageFont.load_default(size=size)
        def point(xx,yy): return ((xx+20)*scale,(yy+20)*scale)
    except ImportError:
        drawing=None
    for item in items:
        x,y=item['x'],item['y']; stroke=item['strokeColor']
        if item['type']=='rectangle':
            svg.append(f'<rect x="{x}" y="{y}" width="{item["width"]}" height="{item["height"]}" rx="4" fill="{item["backgroundColor"]}" stroke="{stroke}"/>')
            if drawing: drawing.rectangle((*point(x,y),*point(x+item['width'],y+item['height'])),fill=item['backgroundColor'],outline=stroke,width=1)
        elif item['type']=='text':
            size=item['fontSize']
            for row,text in enumerate(item['text'].splitlines()):
                yy=y+row*size*1.25
                svg.append(f'<text x="{x}" y="{yy+size}" font-family="monospace" font-size="{size}" fill="{stroke}">{html.escape(text)}</text>')
                if drawing: drawing.text(point(x,yy),text,font=font(size),fill=stroke)
        elif item['type']=='arrow':
            points=[(x+p[0],y+p[1]) for p in item['points']]
            coords=' '.join(f'{xx},{yy}' for xx,yy in points)
            dash=' stroke-dasharray="6 4"' if item['strokeStyle']=='dashed' else ''
            svg.append(f'<polyline points="{coords}" fill="none" stroke="#94a3b8" stroke-width="1" marker-end="url(#arrow)"{dash}/>')
            if drawing:
                drawing.line([point(xx,yy) for xx,yy in points],fill='#94a3b8',width=1)
                end,previous=points[-1],points[-2]
                angle=math.atan2(end[1]-previous[1],end[0]-previous[0]); length=8
                triangle=[end,(end[0]-length*math.cos(angle-.45),end[1]-length*math.sin(angle-.45)),
                          (end[0]-length*math.cos(angle+.45),end[1]-length*math.sin(angle+.45))]
                drawing.polygon([point(xx,yy) for xx,yy in triangle],fill='#64748b')
    svg.append('</svg>'); write(stem.with_suffix('.svg'),'\n'.join(svg)+'\n')
    if drawing: canvas.save(stem.with_suffix('.png'))


def entity_diagram(schema, modules, filename, title, destination):
    tables={table['name']:table for table in schema['tables']}
    main={table for table in tables if PURPOSES[table][0] in modules}
    references={key['referenced_table'] for name in main for key in tables[name]['constraints'] if key['kind']=='f'}
    cards=[]
    for name in sorted(main,key=lambda key:(PURPOSES[key][0],key))+sorted(references-main):
        table=tables[name]; marks=defaultdict(set)
        for key in table['constraints']:
            if key['kind'] in ('p','u','f'):
                for column in key['columns'] or []: marks[column].add({'p':'PK','u':'U','f':'FK'}[key['kind']])
        lines=[]
        for column in table['columns']:
            if column['name'] in marks or (name in main and column['name'] in ('code','name','status','origin','classification','version','sha256','credential_version','email_verified_at')):
                prefix='/'.join(sorted(marks[column['name']]))
                line=f"{prefix} {column['name']}{'?' if column['nullable'] else ''}: {column['type']}".strip()
                lines += textwrap.wrap(line,46,subsequent_indent='  ')
        if name not in main: lines=lines[:3]+['Referencia; detalle en su módulo']
        cards.append({'name':name,'tenant':table['rls'],'stub':name not in main,'lines':lines})
    links=[]
    for source in sorted(main):
        grouped=defaultdict(list)
        for key in tables[source]['constraints']:
            if key['kind']=='f': grouped[key['referenced_table']].append(key)
        for target,keys in grouped.items():
            nullable={column['name']:column['nullable'] for column in tables[source]['columns']}
            links.append({'source':source,'target':target,'constraints':[key['name'] for key in keys],
                          'optional':all(any(nullable[col] for col in key['columns']) for key in keys)})
    return draw_native(name=filename, title=title, cards=cards, links=links,
                       destination=destination,note='Entidades y FK exportadas del SQL aplicado; los catálogos no prueban funcionalidades de aplicación.')


def main():
    parser=argparse.ArgumentParser(); parser.add_argument('schema',type=Path); parser.add_argument('output',type=Path)
    args=parser.parse_args(); schema=json.loads(args.schema.read_text(encoding='utf-8-sig'))
    missing={table['name'] for table in schema['tables']}-PURPOSES.keys()
    if missing: raise SystemExit('Faltan descripciones funcionales: '+', '.join(sorted(missing)))
    args.output.mkdir(parents=True,exist_ok=True)
    dictionary(schema,args.output)
    destination=args.output/'diagrams'; scenes=[]
    specs=[({'identity','integration'},'02-identity-access-integrations','Identidad, acceso e integraciones'),
           ({'component','scan'},'03-scans-sbom-components-graph','Scans, SBOM, versiones y grafo'),
           ({'vulnerability'},'04-vulnerability-intelligence','Vulnerabilidades, fuentes y hallazgos'),
           ({'documentation'},'05-documentation-rag-pgvector','Documentos, citas y metadatos RAG — vectores opcionales'),
           ({'impact','reporting','notifications','audit'},'06-change-impact-reporting-notifications','Impacto, reportes, notificaciones y retención')]
    for modules,name,title in specs:
        # Keep filenames independent of loop variables in the entity builder.
        scene=entity_diagram(schema,modules,name,title,destination)
        wrong=next((key for key in [name] if not (destination/(key+'.excalidraw')).exists()),None)
        if wrong: raise RuntimeError('Unexpected diagram filename: '+wrong)
        scenes.append(scene)
    tenant_count=sum(table['rls'] for table in schema['tables'])
    cards=[{'name':name,'tenant':False,'lines':lines} for name,lines in [
        ('Usuarios / organización',['Identidad y permisos de aplicación']),('Angular',['Interfaz; no inventa evidencia']),
        ('Spring Boot',['Puertos/casos de uso; transacciones']),('PostgreSQL',[f"{len(schema['tables'])} tablas; {tenant_count} con RLS",'Catálogo y snapshots separados']),
        ('Worker aislado',['Jobs/reintentos; scanner sin secretos']),('GitHub App / API',['Identidad e instalación diferenciadas']),
        ('cdxgen / CycloneDX',['Fuente de SBOM; no se ejecutó aquí']),('OSV',['Fuente pública; hallazgos privados']),
        ('Originales privados',['Retención por organización','Temporales se eliminan']),('Extracción / OCR',['Texto, ubicación y versiones']),
        ('Ollama',['Modelo/dimensión por configurar']),('Gemini',['Proveedor del producto; no configurado'])]]
    logical=[('Usuarios / organización','Angular'),('Angular','Spring Boot'),('Spring Boot','PostgreSQL'),('Spring Boot','Worker aislado'),
             ('Worker aislado','GitHub App / API'),('Worker aislado','cdxgen / CycloneDX'),('Worker aislado','OSV'),
             ('Spring Boot','Originales privados'),('Originales privados','Extracción / OCR'),('Extracción / OCR','Ollama'),('Spring Boot','Gemini')]
    draw_native('01-system-data-context','Contexto de datos del sistema',cards,[{'source':a,'target':b,'logical':True,'label':'flujo lógico'} for a,b in logical],destination,
                'Contexto funcional objetivo; conectores lógicos discontinuos. No representa proveedores ya integrados.')
    cards=[{'name':name,'tenant':tenant,'lines':lines} for name,tenant,lines in [
        ('Aplicación autorizada',False,['Cuenta y membresía activas','Permiso de rol y equipo por repositorio']),
        ('TenantTransactions',False,['SET LOCAL app.organization_id','Misma conexión y transacción JPA/JDBC']),
        ('dependency_app',False,['LOGIN no propietario','Sin DDL, SUPERUSER ni BYPASSRLS']),
        ('app_private.current_organization_id',False,['Contexto UUID local','Sin contexto: no ve filas tenant']),
        ('Tablas tenant',True,[f'{tenant_count} tablas con ENABLE + FORCE RLS','USING / WITH CHECK y FK compuestas']),
        ('Catálogos globales',False,['Ecosistemas/advisories/roles','SELECT runtime; escrituras restringidas']),
        ('dependency_migrator',False,['Capability de mantenimiento','Credenciales separadas del runtime']),
        ('Auditoría',True,['INSERT/SELECT para runtime','UPDATE/DELETE denegados']),
        ('Fin de transacción',False,['COMMIT / ROLLBACK','Contexto local no queda en el pool'])]]
    logical=[('Aplicación autorizada','TenantTransactions'),('TenantTransactions','dependency_app'),('dependency_app','Tablas tenant'),
             ('Tablas tenant','app_private.current_organization_id'),('dependency_app','Catálogos globales'),('dependency_app','Auditoría'),
             ('dependency_migrator','Tablas tenant'),('TenantTransactions','Fin de transacción')]
    draw_native('07-tenant-isolation-rls','Aislamiento tenant y roles SQL',cards,[{'source':a,'target':b,'logical':True,'label':'control / flujo'} for a,b in logical],destination,
                'Flujo de seguridad del esquema real; RLS no sustituye autorización. Identidades globales se controlan desde autenticación.')
    all_constraints={key['name']+'@'+table['name'] for table in schema['tables'] for key in table['constraints'] if key['kind']=='f'}
    covered=set()
    for scene in scenes:
        boxes={item['id']:item.get('customData',{}).get('table') for item in scene['elements'] if item['type']=='rectangle'}
        for item in scene['elements']:
            if item['type']=='arrow':
                child=boxes[item['startBinding']['elementId']]
                covered.update(name+'@'+child for name in item.get('customData',{}).get('constraints',[]))
    assert covered==all_constraints, 'Faltan FK en diagramas: '+str(all_constraints-covered)
    summary={'tables':len(schema['tables']),'tenant_tables':tenant_count,'foreign_keys':len(all_constraints),
             'native_diagrams':7,'validated_unique_ids':True,'validated_bindings':True,'all_foreign_keys_covered':True}
    write(args.output/'diagram-validation.json',json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(summary,ensure_ascii=False))


if __name__=='__main__': main()
