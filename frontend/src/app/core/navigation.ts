export const navigation = [
{id:'administration',label:'Administración',icon:'shield',ready:true,description:'Admisiones y control global de la plataforma.'},
{id:'repositories',label:'Mis repositorios',icon:'box',ready:true,description:'Los repositorios a los que puedes acceder.'},
  {
    id: 'dashboard',
    label: 'Dashboard',
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
    ready: false,
    description:
      'Explora hallazgos de seguridad con su versión afectada y evidencia. Pendiente de la integración con OSV.',
  },
  {
    id: 'analysis',
    label: 'Análisis',
    icon: 'analysis',
    ready: false,
    description:
      'Ejecuta análisis sobre commits reales. Primero conectaremos repositorios y el procesamiento de Maven y npm.',
  },
  {
    id: 'documentation',
    label: 'Documentación',
    icon: 'document',
    ready: false,
    description:
      'Organiza documentación técnica y consulta respuestas con evidencia. La búsqueda y el asistente llegarán en su fase.',
  },
  {
    id: 'reports',
    label: 'Reportes',
    icon: 'report',
    ready: false,
    description:
      'Comparte informes trazables sobre dependencias y riesgos. Pendiente de inventario y hallazgos reales.',
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
