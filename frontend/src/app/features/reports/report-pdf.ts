import { jsPDF } from 'jspdf';
import { ReportIssue, ReportSeverity, reportSeverities, severityLabels } from './report-content';

export interface ReportPdfData {
  kind: 'EXECUTIVE' | 'TECHNICAL';
  organization: string;
  repository: string;
  generatedAt: string;
  assessmentDate: string | null;
  coverageNote: string | null;
  issues: ReportIssue[];
}

const ink: [number, number, number] = [23, 35, 51];
const muted: [number, number, number] = [84, 98, 113];
const amber: [number, number, number] = [167, 100, 27];

function safeText(value: string): string {
  return value.replace(/[“”]/g, '"').replace(/[‘’]/g, "'")
    .replace(/[—–]/g, '-').replace(/•/g, '-').replace(/\u00a0/g, ' ')
    .replace(/[^\x09\x0a\x0d\x20-\x7e\u00a1-\u00ff]/g, '');
}

export function createReportPdf(data: ReportPdfData): jsPDF {
  const pdf = new jsPDF({ unit: 'mm', format: 'a4', compress: true });
  const left = 18;
  const right = 192;
  const width = right - left;
  const bottom = 274;
  let y = 32;

  const page = (): void => {
    pdf.addPage();
    pdf.setFillColor(...ink);
    pdf.rect(0, 0, 210, 3, 'F');
    pdf.setFont('helvetica', 'bold');
    pdf.setFontSize(8);
    pdf.setTextColor(...muted);
    pdf.text('DEPENDENCY INTELLIGENCE  /  INFORME DE SEGURIDAD', left, 18);
    y = 32;
  };
  const space = (height: number): void => { if (y + height > bottom) page(); };
  const line = (text: string, size = 10, bold = false, color = ink, indent = 0): void => {
    pdf.setFont('helvetica', bold ? 'bold' : 'normal');
    pdf.setFontSize(size);
    pdf.setTextColor(...color);
    const rows = pdf.splitTextToSize(safeText(text), width - indent) as string[];
    for (const row of rows) {
      space(size * .42 + 1);
      pdf.text(row, left + indent, y);
      y += size * .42 + 1;
    }
  };
  const paragraph = (text: string, size = 10): void => {
    for (const part of text.split(/\n+/).map(value => value.trim()).filter(Boolean)) {
      line(part, size, false, ink);
      y += 1.5;
    }
  };
  const label = (name: string, value: string): void => {
    line(name.toUpperCase(), 8, true, muted);
    line(value, 10);
    y += 2;
  };

  pdf.setFillColor(...ink);
  pdf.rect(0, 0, 210, 8, 'F');
  line('DEPENDENCY INTELLIGENCE  /  INFORME DE SEGURIDAD', 9, true, amber);
  y += 5;
  line(data.kind === 'EXECUTIVE' ? 'Resumen ejecutivo' : 'Informe técnico', 22, true);
  y += 4;
  label('Organización', data.organization);
  label('Repositorio', data.repository);
  label('Generado', data.generatedAt);
  if (data.assessmentDate) label('Evidencia OSV', data.assessmentDate);

  const counts = reportSeverities.map(level => `${severityLabels[level]}: ${data.issues.filter(issue => issue.severity === level).length}`).join('   |   ');
  space(17);
  pdf.setDrawColor(211, 201, 187);
  pdf.line(left, y, right, y);
  y += 6;
  line(`${data.issues.length} ${data.issues.length === 1 ? 'aviso' : 'avisos'} de severidad media a crítica`, 13, true);
  line(counts, 9, false, muted);
  if (data.coverageNote) {
    y += 3;
    label('Alcance de la evidencia', data.coverageNote);
  }
  y += 7;

  if (!data.issues.length) paragraph('No hay avisos de severidad media, alta o crítica para los filtros seleccionados.');
  for (const severity of reportSeverities) {
    const issues = data.issues.filter(issue => issue.severity === severity);
    if (!issues.length) continue;
    space(16);
    line(`${severityLabels[severity]}  /  ${issues.length} ${issues.length === 1 ? 'aviso' : 'avisos'}`, 14, true, amber);
    y += 3;
    for (const issue of issues) {
      space(26);
      pdf.setDrawColor(211, 201, 187);
      pdf.line(left, y, right, y);
      y += 6;
      line(issue.dependency, 12, true);
      line(`${issue.ecosystem}  |  Paquete: ${issue.packageName}  |  Versión: ${issue.version}`, 9, false, muted);
      line(`Aviso: ${issue.advisoryId}`, 8, false, muted);
      y += 3;
      if (data.kind === 'EXECUTIVE') {
        label('Qué puede causar', issue.businessImpact);
        label('Por qué cambiarla', issue.whyChange);
      } else {
        label('Qué hace la vulnerabilidad', issue.technicalEffect);
        line('DESCRIPCIÓN PUBLICADA POR OSV', 8, true, muted);
        paragraph(issue.technicalDescription || issue.title, 9);
        if (issue.fixedVersions.length) label('Versiones corregidas según OSV', issue.fixedVersions.join(', '));
      }
      line(`Fuente: ${issue.url}`, 8, false, amber);
      y += 5;
    }
  }

  const pages = pdf.getNumberOfPages();
  for (let index = 1; index <= pages; index++) {
    pdf.setPage(index);
    pdf.setDrawColor(211, 201, 187);
    pdf.line(left, 281, right, 281);
    pdf.setFont('helvetica', 'normal');
    pdf.setFontSize(8);
    pdf.setTextColor(...muted);
    pdf.text('Avisos OSV guardados. La exposición del código requiere verificación.', left, 286);
    pdf.text(`${index} / ${pages}`, right, 286, { align: 'right' });
  }
  return pdf;
}

export function downloadReportPdf(data: ReportPdfData): void {
  const filename = data.repository.replace(/[^a-z0-9-]+/gi, '-').replace(/^-|-$/g, '').toLowerCase();
  createReportPdf(data).save(`reporte-${data.kind === 'EXECUTIVE' ? 'ejecutivo' : 'tecnico'}-${filename}.pdf`);
}
