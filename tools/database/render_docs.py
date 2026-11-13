#!/usr/bin/env python3
"""Render the dictionary and editable diagrams from a real PostgreSQL catalog snapshot."""
from pathlib import Path
import json
import re
import uuid

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / 'docs/database/schema-catalog.json'
tables = json.loads(CATALOG.read_text())
by_name = {t['name']: t for t in tables}
phases = {}
for path in (ROOT / 'backend/src/main/resources/db').rglob('V*.sql'):
    for name in re.findall(r'CREATE TABLE\s+(\w+)', path.read_text(), re.I):
        phases[name] = path.parent.name

PURPOSE = {
'users':'Identidad global, email normalizado y hash opcional; visible solo para membresías activas del tenant.',
'organizations':'Límite de propiedad y aislamiento del cliente.',
'user_identities':'Identidades externas de autenticación; no accesibles al rol runtime.',
'roles':'Roles base globales. No presupone roles personalizados.',
'permissions':'Permisos atómicos de autorización.',
'role_permissions':'Concesiones de permisos a roles globales.',
'organization_memberships':'Pertenencia y estado de un usuario dentro de una organización.',
'membership_roles':'Roles independientes asignados a una membresía.',
'membership_invitations':'Invitaciones con token hasheado y expiración; nunca el token original.',
'organization_teams':'Agrupaciones internas de una organización.',
'team_memberships':'Miembros de cada equipo.',
'repository_team_access':'Rol concedido a un equipo sobre un repositorio concreto.',
'integrations':'Cuenta de proveedor conectada por tenant; solo referencias a credenciales externas.',
'github_installations':'Instalación de GitHub App asociada a una integración.',
'repositories':'Repositorios autorizados, identidad externa y configuración observada.',
'repository_commits':'Commits observados; SHA limitado al repositorio y organización.',
'repository_branches':'Referencia de rama y último commit observado en el mismo repositorio.',
'repository_settings':'Opciones escalares de ejecución por repositorio.',
'webhook_events':'Entrega idempotente y hash del payload, persistida después de verificar firma.',
'integration_sync_runs':'Historial de sincronización con estados de proceso.',
'background_jobs':'Cola persistente por tenant, reintentos limitados y deduplicación.',
'background_job_attempts':'Intentos numerados de ejecución de un trabajo.',
'background_job_events':'Transiciones y eventos de diagnóstico de un trabajo.',
'scan_profiles':'Versión explícita de configuración de análisis; JSON solo para flags técnicos.',
'scan_profile_ecosystems':'Ecosistemas habilitados en un perfil sin listas JSON.',
'scans':'Snapshot trazable a commit, configuración, herramienta, solicitante y trabajo.',
'scan_stages':'Catálogo global de etapas del pipeline.',
'scan_stage_executions':'Intentos de cada etapa, métricas y errores redactados.',
'scan_logs':'Diagnóstico por scan; sanitización y retención a cargo del productor/worker.',
'scan_artifacts':'Ubicación, hash y tamaño de artefactos derivados de un scan.',
'sbom_documents':'Metadatos CycloneDX ligados a un artefacto del mismo scan.',
'sbom_external_references':'Referencias consultables del documento SBOM.',
'components':'Catálogo lógico de paquetes privado por organización; no es una instalación.',
'component_identifiers':'Identificadores alternativos de un paquete; PURL de versión se conserva como bom_ref cuando corresponda.',
'component_versions':'Versiones textuales de un paquete; no presupone SemVer.',
'scan_components':'Presencia de una versión en un snapshot, identificada por bom_ref.',
'dependency_edges':'Aristas dirigidas que solo unen apariciones del mismo scan.',
'component_hashes':'Digests independientes de artefactos de una versión.',
'component_licenses':'Licencias declaradas por versión de catálogo.',
'scan_component_licenses':'Licencias observadas en la aparición concreta del SBOM.',
'component_external_references':'Referencias externas de una versión.',
'vulnerabilities':'Advisories públicos canónicos por fuente e identificador externo.',
'vulnerability_aliases':'Identificadores alternativos, sin arrays ni equivalencia OSV/CVE implícita.',
'vulnerability_references':'Referencias públicas independientes del advisory.',
'vulnerability_severities':'Evaluaciones por fuente/método, con score y vector CVSS opcionales.',
'vulnerability_affected_packages':'Identidad pública del paquete afectado, independiente del catálogo privado.',
'vulnerability_affected_ranges':'Rango del advisory; interpretación determinista por ecosistema.',
'vulnerability_range_events':'Secuencia de introducción, fix, límite o última versión afectada.',
'vulnerability_fixed_versions':'Versiones corregidas explicitadas por el proveedor.',
'vulnerability_source_records':'Respuesta pública de proveedor hasheada, parser y fecha de consulta.',
'scan_vulnerability_findings':'Coincidencia de advisory con una aparición concreta; conserva procedencia.',
'finding_evidence':'Versión e identificador observados, rango y método de matching.',
'scan_comparisons':'Comparación reproducible entre dos scans del mismo repositorio.',
'scan_component_changes':'Proyección persistida del diff, con método en la comparación y evidencia.',
'report_definitions':'Definiciones globales disponibles de reportes.',
'report_jobs':'Ejecución de reporte; parámetros técnicos, formato y solicitante.',
'report_job_scans':'Snapshots exactos que conforman el scope reproducible del reporte.',
'report_artifacts':'Archivo derivado con hash, ubicación y expiración.',
'audit_events':'Eventos append-only; referencia descriptiva, no FK polimórfica para datos de negocio.',
'audit_event_changes':'Cambios redactados de atributos, también append-only.',
'data_retention_policies':'Política configurable por organización y tipo, desactivada hasta definirla.',
'data_deletion_requests':'Solicitudes de eliminación trazables; ejecución a cargo de un worker autorizado.',
'deletion_request_repositories':'Objetivos de eliminación tipados mediante FK.',
'deletion_request_documents':'Documentos objetivo de una solicitud de eliminación.',
'documents':'Documento lógico, tipo, origen, autor y política de retención opcional.',
'document_versions':'Edición con hash/MIME; original solo temporal con expiración obligatoria.',
'document_repositories':'Asociaciones independientes de documentos y repositorios.',
'document_components':'Asociaciones de documentos con paquetes lógicos.',
'document_component_versions':'Asociaciones de documentos con versiones concretas.',
'document_chunks':'Texto extraído con ordinal, página, sección y hash para citas.',
'document_processing_runs':'Historial de extracción/OCR/indexación y versión de herramienta.',
'document_processing_artifacts':'Artefactos temporales de procesamiento con expiración.',
'assistant_model_providers':'Proveedores configurables; no se siembra uno por defecto.',
'assistant_model_versions':'Versión de modelo y configuración técnica sin secretos.',
'embedding_models':'Dimensión declarada de un modelo, sin elegir proveedor.',
'document_chunk_embeddings':'Vector por chunk/modelo; trigger verifica dimensión y RLS protege tenant.',
'assistant_prompt_versions':'Versión de las instrucciones usadas para reproducibilidad.',
'assistant_conversations':'Conversaciones con propietario miembro de la organización.',
'assistant_messages':'Mensajes ordenados, modelo/prompt y consumo opcional.',
'assistant_message_evidences':'Citas tipadas a chunk, aparición o finding; exactamente una referencia.',
'impact_analyses':'Operación propuesta sobre un scan/commit base; no modifica repositorios.',
'impact_findings':'Hallazgos KNOWN/POTENTIAL/VALIDATED; confianza técnica entre 0 y 1.',
'impact_evidences':'Evidencia sanitizada con origen, fecha, ubicación y hash opcional.',
'impact_recommendations':'Acción sugerida y prioridad; requiere interpretación de aplicación.',
'impact_recommendation_evidences':'Fundamento M:N de recomendaciones mediante evidencias.',
'impact_test_suggestions':'Pruebas sugeridas para comprobar un impacto.',
'version_change_records':'Cambio entre versiones del mismo paquete con fuente consultada.',
'change_record_evidences':'Extractos sanitizados de changelogs/release notes.',
'document_review_flags':'Documento potencialmente afectado por un hallazgo, sujeto a revisión.',
'report_schedules':'Programaciones deshabilitadas por defecto; cron y zona deben validarse en aplicación.',
'report_schedule_recipients':'Destinatarios miembros separados en filas.',
'notification_rules':'Reglas por evento, deshabilitadas por defecto.',
'notification_subscriptions':'Suscripciones independientes de miembros a reglas.',
'notifications':'Evento lógico deduplicado; independiente de los canales de entrega.',
'notification_findings':'Findings relacionados con una notificación mediante FK real.',
'notification_scans':'Scans relacionados con una notificación mediante FK real.',
'notification_deliveries':'Entrega independiente por canal/destinatario, sin duplicados.',
'notification_delivery_attempts':'Intentos numerados de entrega.',
}
for name in by_name:
    if name.startswith('impact_finding_') and name != 'impact_findings':
        PURPOSE[name] = 'Asociación tipada de un hallazgo de impacto; evita FKs polimórficas.'
CATALOGS = {'integration_providers','package_ecosystems','dependency_scopes','dependency_relationship_types','vulnerability_sources','scanner_tools','license_catalog','job_types','report_types','report_formats','document_types','notification_channels','severity_levels'}
for name in CATALOGS: PURPOSE[name] = 'Catálogo de referencia global, códigos estables; no acredita cobertura funcional del proveedor/ecosistema.'

def group(name):
    if name in CATALOGS: return 'reference'
    if name.startswith(('vulnerability_', 'vulnerabilities', 'finding_evidence','scan_vulnerability')): return 'vulnerability'
    if name.startswith(('document','assistant','embedding','deletion_request_documents')): return 'documentation'
    if name.startswith(('impact','version_change','change_record')): return 'impact'
    if name.startswith(('notification',)): return 'notification'
    if name.startswith(('report',)): return 'reporting'
    if name.startswith(('audit','data_retention','data_deletion','deletion_request')): return 'audit'
    if name.startswith(('scan','sbom','component','dependency','background')): return 'scan'
    return 'identity'

def role(t):
    if t['name'] in ('organizations','users'): return 'tenant (identidad protegida por política específica)'
    return 'tenant' if t['rls'] else 'global (solo lectura runtime)' if t['name']!='user_identities' else 'global (sin acceso runtime)'

lines=['# Diccionario de datos', '', 'Generado desde el catálogo PostgreSQL real por `tools/database/render_docs.py`. Incluye las fases core, future y vector; la base local de trabajo no activa vector.', '', 'Todas las FKs usan `ON DELETE RESTRICT`: no se propaga borrado de historia. Retención y eliminación requieren políticas explícitas y un worker; no se elimina contenido por añadir una fecha de expiración. `updated_at` debe actualizarlo el caso de uso.', '', 'Los índices FK facilitan joins y borrados controlados. Índices adicionales: historial por repositorio/fecha, cola pendiente por disponibilidad, inventario por versión y búsqueda textual documental. Las restricciones UNIQUE evitan duplicados; el índice parcial del root permite como máximo una raíz por scan.', '']
for t in tables:
    n=t['name']; assert n in PURPOSE, n
    nature = 'proyección histórica' if n=='scan_component_changes' else 'snapshot/evidencia' if n.startswith(('scan_', 'finding_', 'impact_', 'audit_', 'document_chunk','vulnerability_source')) else 'dato fuente/relación'
    lines += [f'## `{n}`', '', PURPOSE[n], '', f"Módulo: **{group(n)}**. Fase: **{phases[n]}**. Alcance: **{role(t)}**. Naturaleza: {nature}. Propietario SQL: `{t['owner']}`.", '', '| Columna | Tipo | Nullable | Default |', '|---|---|---|---|']
    for c in t['columns']: lines.append(f"| `{c['name']}` | `{c['type']}` | {'sí' if c['nullable'] else 'no'} | `{c['default'] or '—'}` |")
    lines+=['','Restricciones (PK, UNIQUE, FK y CHECK):','']
    lines += [f"- `{c['definition']}`" for c in t['constraints']]
    incoming=[f"{x['name']} ({','.join(c['columns'])})" for x in tables for c in x['constraints'] if c['type']=='f' and c['target']==n]
    lines += ['', 'Relaciones entrantes: '+('; '.join(f'`{i}`' for i in incoming) or 'ninguna')+'.', '', 'Índices:', '']
    lines += [f'- `{i}`' for i in t['indexes']]
    if t['policies']: lines+=['','RLS (`FORCE` activado): '+ '; '.join(f"`{p['name']}` USING `{p['using']}` WITH CHECK `{p['check'] or '—'}`" for p in t['policies'])+'.']
    lines+=['']
(ROOT/'docs/database/data-dictionary.md').write_text('\n'.join(lines))

# Native Excalidraw primitives. Deterministic IDs permit validation/diff and editing.
def uid(s): return uuid.uuid5(uuid.NAMESPACE_URL,s).hex[:24]
class Drawing:
    def __init__(self,key,title,subtitle):
        self.key=key;self.elements=[];self.boxes={};self.text(0,-150,title,32);self.text(0,-95,subtitle,18)
        self.text(0,-55,'Azul: global · Violeta: tenant/RLS · Verde: puente · Gris: referencia de otro módulo. FK: hijo N → padre 1 (o 0..1).',16)
    def element(self,kind,x,y,w,h,key,**kw):
        e=dict(id=uid(self.key+key),type=kind,x=x,y=y,width=w,height=h,angle=0,strokeColor='#334155',backgroundColor='transparent',fillStyle='solid',strokeWidth=1,strokeStyle='solid',roughness=0,opacity=100,groupIds=[],frameId=None,roundness=None,seed=1,version=1,versionNonce=1,isDeleted=False,boundElements=None,updated=1,link=None,locked=False)
        e.update(kw);self.elements.append(e);return e
    def text(self,x,y,content,size=16,key=None,**kw):
        rows=content.splitlines();w=max(map(len,rows),default=0)*size*.56;h=len(rows)*size*1.25
        return self.element('text',x,y,w,h,key or 'text'+str(len(self.elements)),text=content,originalText=content,fontSize=size,fontFamily=2,textAlign='left',verticalAlign='top',containerId=kw.pop('containerId',None),lineHeight=1.25,autoResize=True,**kw)
    def box(self,name,x,y,body,color='#ede9fe',meta=None):
        content=name+'\n'+body;h=max(130,len(content.splitlines())*20+32);w=520
        r=self.element('rectangle',x,y,w,h,name,backgroundColor=color,customData=meta or {})
        text=self.text(x+16,y+16,content,16,key=name+'-label',containerId=r['id']);r['boundElements']=[dict(id=text['id'],type='text')]
        self.boxes[name]=r;return r
    def arrow(self,a,b,key,label='',meta=None):
        s=self.boxes[a];t=self.boxes[b]
        if a==b: return
        if abs(s['y']-t['y'])<1:
            sign=1 if t['x']>s['x'] else -1
            x=s['x']+(s['width']+10 if sign>0 else -10);y=s['y']+s['height']/2
            tx=t['x']+(-10 if sign>0 else t['width']+10);ty=t['y']+t['height']/2
        else:
            sign=1 if t['y']>s['y'] else -1
            x=s['x']+s['width']/2;y=s['y']+(s['height']+10 if sign>0 else -10)
            tx=t['x']+t['width']/2;ty=t['y']+(-10 if sign>0 else t['height']+10)
        e=self.element('arrow',x,y,abs(tx-x),abs(ty-y),key,points=[[0,0],[(tx-x)/2,(ty-y)/2],[tx-x,ty-y]],startBinding=dict(elementId=s['id'],focus=0,gap=10),endBinding=dict(elementId=t['id'],focus=0,gap=10),startArrowhead=None,endArrowhead='arrow',customData=meta or {})
        for r in (s,t):r['boundElements'].append(dict(id=e['id'],type='arrow'))
        if label:self.text(x+8,y+8,label,12,key=key+'-cardinality')
    def write(self):
        data=dict(type='excalidraw',version=2,source='https://excalidraw.com',elements=self.elements,appState=dict(viewBackgroundColor='#ffffff',gridSize=20),files={})
        (ROOT/'diagramas'/f'{self.key}.excalidraw').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')

def table_diagram(key,title,names):
    names=sorted(names); external=sorted({c['target'] for n in names for c in by_name[n]['constraints'] if c['type']=='f'}-set(names))
    d=Drawing(key,title,'Esquema SQL real. Entidades grises son referencias externas al módulo; cada conector corresponde a una FK real.')
    all_names=names+external;row_y=0
    for row in range(0,len(all_names),4):
        maxh=0
        for col,n in enumerate(all_names[row:row+4]):
            t=by_name[n];fks=[c for c in t['constraints'] if c['type']=='f'];uniques=[c for c in t['constraints'] if c['type']=='u']
            attrs=['PK id: uuid']
            fkcols={i for c in fks for i in c['columns']}
            attrs += [f"{'FK ' if c['name'] in fkcols else ''}{c['name']}: {c['type']}" for c in t['columns'] if c['name']!='id' and (c['name'] in fkcols or c['name'] in ('name','code','status','version','bom_ref','commit_sha','classification','dimensions','embedding'))]
            attrs += ['UQ '+','.join(c['columns']) for c in uniques]
            # Avoid text spilling outside a box; wrapping does not alter native editability.
            import textwrap
            body='\n'.join(part for a in attrs for part in textwrap.wrap(a,52))
            color='#f1f5f9' if n in external else '#dcfce7' if len(fks)>=2 and len(uniques)>=1 else '#ede9fe' if t['rls'] else '#dbeafe'
            r=d.box(n,col*800,row_y,body,color,dict(table=n,referenceOnly=n in external));maxh=max(maxh,r['height'])
        row_y+=maxh+210
    for n in names:
        for c in by_name[n]['constraints']:
            if c['type']=='f':
                nullable=any(x['nullable'] for x in by_name[n]['columns'] if x['name'] in c['columns'])
                d.arrow(n,c['target'],n+'-'+c['name'],'N → '+('0..1' if nullable else '1'),dict(foreignKey=c['name'],sourceTable=n,targetTable=c['target']))
    d.write()

d=Drawing('01-system-data-context','Dependency Intelligence · contexto de datos','Core local con Docker; módulos documentales/IA representan evolución y no servicios implementados.')
context=[('Usuario','Identidad y membresía',0,0),('Angular','Frontend local',800,0),('Spring Boot','Autorización + TenantTransactions',1600,0),('PostgreSQL','Fuente relacional · Flyway · RLS',2400,0),('GitHub App/API','Repositorios y commit autorizado',0,430),('Worker de análisis','Job persistente + procesamiento asíncrono',800,430),('cdxgen / CycloneDX','Scanner aislado · no Docker socket',1600,430),('OSV','Advisories públicos y procedencia',2400,430),('PDF temporal','Original eliminado tras indexación/expiración\nRequiere worker de retención',0,860),('Extractor / OCR','Texto + página/sección + hash',800,860),('Embeddings / LLM','Proveedor y modelo por definir',1600,860),('Chunks / pgvector','Texto persistido · vector opcional\nFiltros de tenant y permisos',2400,860)]
for n,b,x,y in context:d.box(n,x,y,b,'#dbeafe' if y<860 else '#f1f5f9')
for i,(a,b) in enumerate([('Usuario','Angular'),('Angular','Spring Boot'),('Spring Boot','PostgreSQL'),('GitHub App/API','Worker de análisis'),('Worker de análisis','cdxgen / CycloneDX'),('cdxgen / CycloneDX','PostgreSQL'),('OSV','Worker de análisis'),('PDF temporal','Extractor / OCR'),('Extractor / OCR','Embeddings / LLM'),('Embeddings / LLM','Chunks / pgvector'),('Chunks / pgvector','PostgreSQL')]):d.arrow(a,b,'flow'+str(i))
d.write()
sets={
 '02-identity-access-integrations':('Identidad, acceso e integraciones',{'identity'}),
 '03-scans-sbom-components-graph':('Scans, trabajos, SBOM y catálogo de componentes',{'scan'}),
 '04-vulnerability-intelligence':('Vulnerabilidades, procedencia y evidencias',{'vulnerability'}),
 '05-documentation-rag-pgvector':('Documentación, citas y embeddings opcionales',{'documentation'}),
 '06-change-impact-reporting-notifications':('Impacto, reportes, notificaciones y auditoría',{'impact','reporting','notification','audit'}),
}
for key,(title,groups) in sets.items():table_diagram(key,title,{n for n in by_name if group(n) in groups})
# Add every global reference catalog, so every implemented table is represented in at least one ER sheet.
table_diagram('02-identity-access-integrations','Identidad, acceso e integraciones',{n for n in by_name if group(n) in {'identity','reference'}})
d=Drawing('07-tenant-isolation-rls','Aislamiento de tenant · RLS','Las políticas son de base de datos; la autorización de membership/RBAC debe ocurrir antes del caso de uso.')
for n,b,x,y in [
 ('Identidad validada','Autenticación por implementar\nValidar membership y permisos',0,0),
 ('TenantTransactions','Nueva transacción Spring\nset_config(app.organization_id, UUID, true)',800,0),
 ('dependency_app','LOGIN · NOSUPERUSER · NOBYPASSRLS\nNo propietario · sin DDL\nCatálogos globales: SELECT',1600,0),
 ('RLS con FORCE','organization_id = app.current_organization_id()\nSin contexto: cero filas / escritura rechazada',0,430),
 ('FKs compuestas','(organization_id, foreign_id)\n→ (organization_id, id)\nGrafo: añade scan_id · comparación: repository_id',800,430),
 ('COMMIT / ROLLBACK','El contexto LOCAL expira\nConexión del pool sin tenant anterior',1600,430),
 ('Identidad global protegida','users: SELECT según membresía activa\norganizations: id = tenant\nuser_identities: sin grants runtime',0,860),
 ('dependency_migrator','Propietario de tablas · DDL\nLOGIN separado · NOSUPERUSER · NOBYPASSRLS\nFlyway usa credencial independiente',800,860),
 ('Administrador local','Bootstrap roles · instalación opcional vector\nProvisionamiento explícito de identidad\nCredenciales fuera de Git',1600,860)]:d.box(n,x,y,b)
for i,(a,b) in enumerate([('Identidad validada','TenantTransactions'),('TenantTransactions','dependency_app'),('dependency_app','RLS con FORCE'),('RLS con FORCE','FKs compuestas'),('FKs compuestas','COMMIT / ROLLBACK'),('RLS con FORCE','Identidad global protegida'),('Administrador local','dependency_migrator')]):d.arrow(a,b,'rlsflow'+str(i))
d.write()
# Check native scene bindings and SQL correspondence, independent of the renderer.
seen=set();fk_seen=set()
for p in (ROOT/'diagramas').glob('*.excalidraw'):
    data=json.loads(p.read_text());assert data['type']=='excalidraw' and data['version']==2
    assert isinstance(data['appState'],dict) and data['files']=={}
    ids={e['id'] for e in data['elements']};assert len(ids)==len(data['elements'])
    for e in data['elements']:
        custom=e.get('customData',{})
        if 'table' in custom:seen.add(custom['table'])
        if 'foreignKey' in custom:
            fk_seen.add((custom['sourceTable'],custom['foreignKey']))
            assert any(c['name']==custom['foreignKey'] and c['target']==custom['targetTable'] for c in by_name[custom['sourceTable']]['constraints'])
        for binding in ('startBinding','endBinding'):
            if e.get(binding):assert e[binding]['elementId'] in ids
        if e.get('containerId'):assert e['containerId'] in ids
        for bound in e.get('boundElements') or []:assert bound['id'] in ids
assert set(by_name)==seen,(set(by_name)-seen)
assert {(t['name'],c['name']) for t in tables for c in t['constraints'] if c['type']=='f'}==fk_seen
print(f'Validated 7 native Excalidraw files; {len(tables)} tables and {len(fk_seen)} real FK relationships covered')
