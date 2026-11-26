import { plainOsvText } from '../../core/osv-text';

export type ReportSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM';

export interface ReportIssue {
  ecosystem: string;
  dependency: string;
  packageName: string;
  version: string;
  advisoryId: string;
  severity: ReportSeverity;
  title: string;
  technicalEffect: string;
  technicalDescription: string;
  businessImpact: string;
  whyChange: string;
  fixedVersions: string[];
  url: string;
  assessedAt: string | null;
}

export const reportSeverities: ReportSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM'];
export const severityLabels: Record<ReportSeverity, string> = {
  CRITICAL: 'Crítica', HIGH: 'Alta', MEDIUM: 'Media',
};

interface Effect { technical: string; business: string; }

/** Describe only effects supported by the advisory's own wording. */
export function advisoryEffect(summary: string, details: string): Effect {
  const text = `${summary} ${details.slice(0, 600)}`.toLowerCase();
  const match = (pattern: RegExp) => pattern.test(text);
  if (match(/ssrf|server.side request forgery|proxy bypass|dns lookup|internal ip ranges/)) return {
    technical: 'Puede permitir que una solicitud salga sin respetar controles de destino, DNS o proxy y alcance servicios internos.',
    business: 'Puede hacer que el servicio contacte sistemas internos que deberían estar protegidos y exponga información de ellos.',
  };
  if (match(/denial.of.service|redos|resource exhaustion|infinite loop|uncontrolled recursion|catastrophic backtracking/)) return {
    technical: 'Una entrada especialmente preparada puede consumir recursos de procesamiento y degradar o detener el servicio.',
    business: 'Puede volver el servicio lento o dejarlo temporalmente fuera de servicio para sus usuarios.',
  };
  if (match(/sql injection/)) return {
    technical: 'Una entrada no validada puede alterar la consulta enviada a la base de datos.',
    business: 'Puede permitir leer o modificar información que la persona no debería poder consultar.',
  };
  if (match(/command injection|remote code execution|arbitrary code execution|code injection/)) return {
    technical: 'Una entrada controlada por un tercero puede llegar a ejecutarse como código o como una orden del sistema.',
    business: 'Puede permitir que alguien realice acciones no autorizadas dentro del servicio.',
  };
  if (match(/cross.site scripting|\bxss\b|script injection/)) return {
    technical: 'Contenido no confiable puede ejecutarse como script en el navegador de otra persona.',
    business: 'Puede mostrar contenido engañoso o afectar la sesión de una persona que use la aplicación.',
  };
  if (match(/authentication bypass|authorization bypass|access control|privilege escalation/)) return {
    technical: 'Una ruta de acceso puede omitir una comprobación de identidad o permisos.',
    business: 'Puede permitir que alguien vea o haga cosas para las que no tiene permiso.',
  };
  if (match(/path traversal|directory traversal|arbitrary file read/)) return {
    technical: 'Una ruta manipulada puede acceder a archivos fuera del directorio previsto.',
    business: 'Puede exponer archivos que no deberían estar disponibles para esa solicitud.',
  };
  if (match(/information disclosure|sensitive data exposure|data leak|credential exposure/)) return {
    technical: 'La vulnerabilidad puede devolver información sensible a quien no debería recibirla.',
    business: 'Puede revelar información privada o credenciales.',
  };
  if (match(/prototype pollution/)) return {
    technical: 'Datos no confiables pueden modificar propiedades heredadas de objetos de JavaScript y alterar el comportamiento de la aplicación.',
    business: 'Puede cambiar resultados o decisiones de la aplicación de una forma que el equipo no esperaba.',
  };
  if (match(/open redirect/)) return {
    technical: 'Una redirección puede aceptar un destino controlado por un tercero.',
    business: 'Puede enviar a las personas a una página engañosa desde un enlace que parece confiable.',
  };
  return {
    technical: 'OSV no precisa el mecanismo en un formato que permita resumirlo sin suponer. La descripción publicada se conserva a continuación.',
    business: 'OSV identifica un problema en esta versión, pero su efecto no puede resumirse con precisión a partir del aviso disponible.',
  };
}

export function reportIssue(input: Omit<ReportIssue, 'title' | 'technicalEffect' | 'technicalDescription' | 'businessImpact' | 'whyChange'> & {
  summary: string | null; details: string | null;
}): ReportIssue {
  const title = plainOsvText(input.summary || '') || `Aviso ${input.advisoryId}`;
  const technicalDescription = plainOsvText(input.details || input.summary || '');
  const effect = advisoryEffect(title, technicalDescription);
  const fix = input.fixedVersions.length
    ? `OSV indica una versión corregida (${input.fixedVersions.join(', ')}). Cambiarla reduce la exposición a este problema conocido.`
    : 'OSV no indica una versión corregida; se necesita evaluar una alternativa o una medida temporal.';
  const { summary: _summary, details: _details, ...base } = input;
  return { ...base, title, technicalEffect: effect.technical, technicalDescription,
    businessImpact: effect.business, whyChange: fix };
}
