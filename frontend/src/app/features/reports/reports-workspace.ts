import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { finalize, switchMap } from 'rxjs';
import { accessError, SessionApiService } from '../../core/session-api.service';
import { TranslatePipe } from '../../core/translate.pipe';
import { fixesForPackage } from '../inventory/inventory-evidence';

interface Repository { id: string; organizationId: string; organizationName: string; fullName: string; source: string; }
interface CatalogPage { packages: unknown[]; totalPackages: number; totalVersions: number; }
interface Advisory { id: string; summary: string | null; details: string | null; severity: string; url: string; affectedJson: string; }
interface VersionAssessment {
  packageVersion: { ecosystem: string; namespace: string | null; name: string; version: string };
  status: string; checkedAt: string | null; advisories: Advisory[];
}
interface InventoryAssessment { generatedAt: string; assessments: VersionAssessment[]; }
interface ReportIssue {
  ecosystem: string; packageName: string; version: string; advisoryId: string; summary: string; details: string;
  severity: string; fixedVersions: string[]; url: string; assessedAt: string | null; assessmentStatus: string;
}
interface ExecutiveIssue extends ReportIssue { whyChange: string; nextStep: string; }
type ReportKind = 'EXECUTIVE' | 'TECHNICAL';
type Format = 'JSON' | 'CSV' | 'PDF';

@Component({
  selector: 'app-reports-workspace',
  imports: [DatePipe, TranslatePipe],
  templateUrl: './reports-workspace.html',
  styleUrl: './reports-workspace.scss',
})
export class ReportsWorkspace implements OnInit {
  private readonly api = inject(SessionApiService);
  readonly repositories = signal<Repository[]>([]);
  readonly repositoryId = signal('');
  readonly ecosystem = signal('ALL');
  readonly severity = signal('ALL');
  readonly kind = signal<ReportKind>('EXECUTIVE');
  readonly assessments = signal<VersionAssessment[]>([]);
  readonly loading = signal(true);
  readonly exporting = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly exportError = signal('');
  readonly generatedAt = signal<Date | null>(null);
  readonly assessmentDate = signal<string | null>(null);
  private previewRequest = 0;

  ngOnInit(): void {
    this.api.get<Repository[]>('/api/account/workspace').subscribe({
      next: repos => { this.repositories.set(repos); this.repositoryId.set((repos.find(repo => repo.id === new URLSearchParams(window.location.search).get('repositoryId')) ?? repos[0])?.id ?? ''); if (repos.length) this.loadPreview(); else this.loading.set(false); },
      error: failure => { this.loading.set(false); this.error.set(accessError(failure)); },
    });
  }

  changeRepository(event: Event): void { this.repositoryId.set((event.target as HTMLSelectElement).value); this.loadPreview(); }
  changeFilter(event: Event, filter: 'ecosystem' | 'severity'): void {
    const value = (event.target as HTMLSelectElement).value;
    if (filter === 'ecosystem') this.ecosystem.set(value); else this.severity.set(value);
    this.loadPreview();
  }
  setKind(kind: ReportKind): void { this.kind.set(kind); }
  private printReport(): void {
    document.body.classList.add('di-printing-report');
    window.addEventListener('afterprint', () => document.body.classList.remove('di-printing-report'), { once: true });
    window.print();
  }

  loadPreview(): void {
    const repo = this.currentRepository(); if (!repo) return;
    const request = ++this.previewRequest;
    this.loading.set(true);
    this.error.set('');
    this.exportError.set('');
    this.notice.set('');
    this.assessments.set([]);
    this.generatedAt.set(null);
    this.assessmentDate.set(null);
    this.api.get<CatalogPage>(this.reportEndpoint(repo, 'preview')).pipe(
      switchMap(() => this.api.get<InventoryAssessment>(`/api/organizations/${repo.organizationId}/repositories/${repo.id}/vulnerability-analysis`)),
      finalize(() => { if (request === this.previewRequest) this.loading.set(false); }),
    ).subscribe({
      next: assessment => {
        if (request !== this.previewRequest) return;
        this.assessments.set(assessment.assessments);
        this.generatedAt.set(new Date());
        this.assessmentDate.set(assessment.generatedAt);
      },
      error: failure => { if (request === this.previewRequest) this.error.set(accessError(failure)); },
    });
  }

  export(format: Format): void {
    const repo = this.currentRepository();
    if (!repo || this.exporting() || !this.generatedAt()) return;
    const preview = this.previewRequest;
    const kind = this.kind();
    this.exporting.set(true);
    this.exportError.set('');
    this.notice.set('');
    this.api.get<CatalogPage>(this.reportEndpoint(repo, 'export-data'))
      .pipe(finalize(() => this.exporting.set(false)))
      .subscribe({
        next: () => {
          if (preview !== this.previewRequest || kind !== this.kind()) return;
          try {
            const payload = this.reportPayload(repo);
            if (format === 'PDF') {
              this.printReport();
              this.notice.set('Usa “Guardar como PDF” en el diálogo de impresión.');
            } else if (format === 'JSON') {
              this.download(JSON.stringify(payload, null, 2), 'application/json;charset=utf-8', 'dependency-intelligence-report.json');
            } else {
              this.download(this.csv(payload), 'text/csv;charset=utf-8', 'dependency-intelligence-report.csv');
            }
          } catch {
            this.exportError.set('No se pudo construir el archivo del reporte. Vuelve a intentarlo.');
          }
        },
        error: failure => {
          if (preview === this.previewRequest) this.exportError.set(accessError(failure));
        },
      });
  }

  currentRepository(): Repository | null { return this.repositories().find(item => item.id === this.repositoryId()) ?? null; }
  reportIssues(): ReportIssue[] {
    return this.assessments().filter(item => ['MATCHED','STALE'].includes(item.status)).flatMap(item => item.advisories.map(advisory => {
      const pkg = item.packageVersion;
      const ecosystem = pkg.ecosystem.toUpperCase();
      const packageName = ecosystem === 'MAVEN' && pkg.namespace ? `${pkg.namespace}:${pkg.name}` : pkg.name;
      return { ecosystem, packageName, version: pkg.version, advisoryId: advisory.id, details: advisory.details || '',
        summary: advisory.summary || advisory.details || 'OSV publica un aviso para esta versión.', severity: advisory.severity || 'UNKNOWN',
        fixedVersions: fixesForPackage(advisory.affectedJson, pkg.ecosystem, packageName), url: advisory.url,
        assessedAt: item.checkedAt, assessmentStatus: item.status };
    })).filter(item => {
      const ecosystemMatches = this.ecosystem() === 'ALL' || item.ecosystem.toUpperCase() === this.ecosystem();
      const severityMatches = this.severity() === 'ALL' || item.severity.toUpperCase() === this.severity();
      return ecosystemMatches && severityMatches;
    });
  }
  packagesWithAdvisories(): number { return new Set(this.reportIssues().map(item => `${item.ecosystem}/${item.packageName}`)).size; }
  affectedVersions(): number { return new Set(this.reportIssues().map(item => `${item.ecosystem}/${item.packageName}/${item.version}`)).size; }
  issueCount(): number { return this.reportIssues().length; }
  coverageNote(): string | null {
    const scoped = this.assessments().filter(item => this.ecosystem() === 'ALL' || item.packageVersion.ecosystem.toUpperCase() === this.ecosystem());
    if (!scoped.length) return 'No hay evaluaciones OSV guardadas para el alcance seleccionado.';
    const partial = scoped.some(item => !['MATCHED', 'NO_KNOWN_ADVISORIES'].includes(item.status));
    return partial ? 'Algunas versiones no tienen una consulta OSV completa o reciente. Revisa la cobertura antes de usar el informe.' : null;
  }
  severityCount(severity: string): number { return this.reportIssues().filter(item => item.severity.toUpperCase() === severity).length; }
  executiveIssues(): ExecutiveIssue[] {
    return this.reportIssues().map(item => ({ ...item, whyChange: this.executiveReason(item.severity), nextStep: this.executiveNextStep(item) }));
  }
  executiveReason(severity: string): string {
    switch (severity.toUpperCase()) {
      case 'CRITICAL': return 'OSV clasifica el aviso como crítico, el nivel de prioridad más alto. Conviene atenderlo primero.';
      case 'HIGH': return 'OSV clasifica el aviso como alto. Conviene planificar su corrección pronto.';
      case 'MEDIUM': return 'OSV reporta un impacto medio. Programa la corrección y revisa si el uso del paquete aumenta la exposición.';
      case 'LOW': return 'OSV reporta un impacto bajo. Incluye la corrección en el mantenimiento y conserva el seguimiento.';
      default: return 'Revisa el aviso para determinar la prioridad cuando OSV no publica severidad.';
    }
  }
  executiveNextStep(item: ReportIssue): string {
    return item.fixedVersions.length
      ? 'Actualizar a la versión corregida y validar la aplicación.'
      : 'Revisar el aviso oficial y acordar una corrección o mitigación.';
  }

  private reportEndpoint(repo: Repository, action: 'preview' | 'export-data'): string {
    const query = new URLSearchParams({ repositoryId: repo.id, ecosystem: this.ecosystem(), severity: this.severity(), page: '0', size: '1' });
    return `/api/organizations/${repo.organizationId}/reports/${action}?${query}`;
  }

  private reportPayload(repo: Repository): Record<string, unknown> {
    const issues = this.reportIssues();
    const executive = this.kind() === 'EXECUTIVE';
    return {
      title: executive ? 'Dependency Intelligence — Executive report' : 'Dependency Intelligence — Technical report',
      generatedAt: this.generatedAt()?.toISOString(), assessmentDate: this.assessmentDate(), organization: repo.organizationName, repository: repo.fullName,
      source: repo.source, ecosystemFilter: this.ecosystem(), severityFilter: this.severity(),
      totals: { affectedPackages: this.packagesWithAdvisories(), affectedVersions: this.affectedVersions(), advisoryVersionRecords: issues.length,
        critical: this.severityCount('CRITICAL'), high: this.severityCount('HIGH'), medium: this.severityCount('MEDIUM'), low: this.severityCount('LOW') },
      partial: this.coverageNote() !== null,
      coverageNote: this.coverageNote(),
      executiveSummary: `${issues.length} registros OSV afectan ${this.affectedVersions()} versiones de ${this.packagesWithAdvisories()} paquetes. Prioriza primero ${this.severityCount('CRITICAL')} críticos y ${this.severityCount('HIGH')} altos; confirma una versión corregida antes de planificar su actualización.`,
      findings: executive ? this.executiveIssues() : issues,
      limitations: ['El informe refleja avisos OSV guardados para las versiones analizadas. Una consulta antigua o incompleta puede omitir información nueva.'],
    };
  }
  private csv(payload: Record<string, unknown>): string {
    const rows = payload['findings'] as Array<ReportIssue | ExecutiveIssue>;
    const quote = (value: unknown) => `"${String(value ?? '').replaceAll('"', '""')}"`;
    const totals = payload['totals'] as { affectedPackages: number; affectedVersions: number; critical: number; high: number; medium: number; low: number };
    return '\uFEFF' + [
      ['Report metadata'],
      ['Title', payload['title']],
      ['Generated at', payload['generatedAt']],
      ['Organization', payload['organization']],
      ['Repository', payload['repository']],
      ['Source', payload['source']],
      ['Ecosystem filter', payload['ecosystemFilter']],
      ['Severity filter', payload['severityFilter']],
      ['OSV evidence date', payload['assessmentDate']],
      ['Partial coverage', payload['partial']],
      ['Coverage note', payload['coverageNote']],
      ['Affected packages', totals.affectedPackages],
      ['Affected versions', totals.affectedVersions],
      ['Critical', totals.critical], ['High', totals.high], ['Medium', totals.medium], ['Low', totals.low],
      ['Executive summary', payload['executiveSummary']],
      [],
      this.kind() === 'EXECUTIVE'
        ? ['Ecosystem','Affected package','Affected version','Severity','Problem reported','Why change it','Next step','Advisory','OSV source']
        : ['Ecosystem','Affected package','Affected version','Severity','Advisory','Problem summary','Technical details','Fixed versions','OSV source','OSV assessment date','OSV assessment status'],
      ...rows.map(row => this.kind() === 'EXECUTIVE'
        ? [row.ecosystem,row.packageName,row.version,row.severity,row.summary,(row as ExecutiveIssue).whyChange,`${(row as ExecutiveIssue).nextStep} ${(row as ExecutiveIssue).fixedVersions.join(', ')}`.trim(),row.advisoryId,row.url]
        : [row.ecosystem,row.packageName,row.version,row.severity,row.advisoryId,row.summary,row.details,row.fixedVersions.join(', '),row.url,row.assessedAt,row.assessmentStatus]),
    ].map(row => row.map(quote).join(',')).join('\r\n');
  }
  private download(content: string, type: string, filename: string): void {
    const url = URL.createObjectURL(new Blob([content], { type }));
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click(); URL.revokeObjectURL(url);
  }
}
