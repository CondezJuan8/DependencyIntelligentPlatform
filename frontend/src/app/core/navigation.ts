export const navigation = [
{id:'administration',label:'Administración',icon:'shield',ready:true,description:'Admisiones y control global de la plataforma.'},
{id:'repositories',label:'Mis repositorios',icon:'box',ready:true,description:'Los repositorios a los que puedes acceder.'},
  {
    id: 'dashboard',
    label: 'Centro de revisión',
    icon: 'dashboard',
    ready: true,
    description: 'Tu espacio de trabajo y los próximos pasos.',
  },
  {
    id: 'inventory',
    label: 'Inventario',
    icon: 'box',
    ready: true,
    description:
      'Consulta y filtra las dependencias de tus repositorios. Disponible cuando conectemos el flujo de análisis.',
  },
  {
    id: 'vulnerabilities',
    label: 'Vulnerabilidades',
    icon: 'shield',
    ready: true,
    description:
      'Prioriza hallazgos OSV por severidad, repositorio y estado de revisión.',
  },
  {
    id: 'analysis',
    label: 'Análisis',
    icon: 'analysis',
    ready: true,
    description:
      'Inicia análisis Maven y npm, consulta su historial, commit, cobertura y errores.',
  },
  {
    id: 'documentation',
    label: 'Documentación',
    icon: 'document',
    ready: true,
    description:
      'Guarda, versiona y busca documentos técnicos asociados a repositorios.',
  },
  {
    id: 'assistant',
    label: 'Nexo',
    icon: 'analysis',
    ready: true,
    description: 'Respuestas basadas en documentación autorizada, con citas verificables.',
  },
  {
    id: 'reports',
    label: 'Reportes',
    icon: 'report',
    ready: true,
    description:
      'Previsualiza y exporta informes de dependencias y riesgos con datos autorizados.',
  },
  {
    id: 'organization',
    label: 'Organización',
    icon: 'users',
    ready: true,
    description: 'Administra tu organización, sus miembros y permisos.',
  },
  {
    id: 'settings',
    label: 'Configuración',
    icon: 'settings',
    ready: true,
    description: 'Tu cuenta y métodos de acceso.',
  },
] as const;
export type Page = (typeof navigation)[number]['id'];
export function readPage(search: string): Page {
  const value = new URLSearchParams(search).get('view');
  return navigation.find((item) => item.id === value)?.id ?? 'dashboard';
}

export function availableNavigation(account:{platformAdmin:boolean;accountType:string}|null){
 return navigation.filter(item=>(item.id!=='administration'||!!account?.platformAdmin)&&(!(account?.accountType==='MEMBER')||item.id!=='organization'));
}
export function initialPage(search:string,account:{platformAdmin:boolean;accountType:string}|null):Page{
 const fallback:Page=account?.platformAdmin?'administration':'dashboard';
 const requested=new URLSearchParams(search).get('view');
 return availableNavigation(account).find(item=>item.id===requested)?.id??fallback;
}
