import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { accessError, SessionApiService } from '../../core/session-api.service';
import { TranslatePipe } from '../../core/translate.pipe';
import { fixesForPackage } from '../inventory/inventory-evidence';

interface Repository { id: string; organizationId: string; organizationName: string; fullName: string; source: string; }
interface CatalogPage { packages: unknown[]; totalPackages: number; totalVersions: number; }
interface Advisory { id: string; summary: string | null; details: string | null; severity: string; url: string; affectedJson: string; }
interface VersionAssessment {
  packageVersion: { ecosystem: string; namespace: string | null; name: string; version: string };
  status: string; advisories: Advisory[];
}
interface InventoryAssessment { assessments: VersionAssessment[]; }
interface ReportIssue {
  ecosystem: string; packageName: string; version: string; advisoryId: string; summary: string; details: string;
  severity: string; fixedVersions: string[]; url: string;
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
  readonly generatedAt = new Date();

  ngOnInit(): void {
    this.api.get<Repository[]>('/api/account/workspace').pipe(finalize(() => this.loading.set(false))).subscribe({
      next: repos => { this.repositories.set(repos); this.repositoryId.set(repos[0]?.id ?? ''); if (repos.length) this.loadPreview(); },
      error: failure => this.error.set(accessError(failure)),
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
    this.loading.set(true); this.error.set(''); this.notice.set(''); this.assessments.set([]);
    this.readPreview(repo, () => {
      this.api.get<InventoryAssessment>(`/api/organizations/${repo.organizationId}/repositories/${repo.id}/vulnerability-analysis`).pipe(finalize(() => this.loading.set(false))).subscribe({
        next: assessment => this.assessments.set(assessment.assessments),
        error: failure => this.error.set(accessError(failure)),
      });
    }, failure => { this.loading.set(false); this.error.set(accessError(failure)); });
  }

  export(format: Format): void {
    const repo = this.currentRepository(); if (!repo || this.exporting()) return;
    this.exporting.set(true); this.error.set(''); this.notice.set('');
    try {
      const payload = this.reportPayload(repo);
      if (format === 'PDF') { this.printReport(); this.notice.set('Usa “Guardar como PDF” en el diálogo de impresión.'); }
      else if (format === 'JSON') this.download(JSON.stringify(payload, null, 2), 'application/json;charset=utf-8', 'dependency-intelligence-report.json');
      else this.download(this.csv(payload), 'text/csv;charset=utf-8', 'dependency-intelligence-report.csv');
    } catch { this.error.set('No se pudo construir el archivo del reporte. Vuelve a intentarlo.'); }
    finally { this.exporting.set(false); }
  }

  currentRepository(): Repository | null { return this.repositories().find(item => item.id === this.repositoryId()) ?? null; }
  reportIssues(): ReportIssue[] {
    return this.assessments().filter(item => ['MATCHED','STALE'].includes(item.status)).flatMap(item => item.advisories.map(advisory => {
      const pkg = item.packageVersion;
      const packageName = pkg.namespace ? `${pkg.namespace}:${pkg.name}` : pkg.name;
      return { ecosystem: pkg.ecosystem, packageName, version: pkg.version, advisoryId: advisory.id, details: advisory.details || '',
        summary: advisory.summary || advisory.details || 'OSV publica un aviso para esta versión.', severity: advisory.severity || 'UNKNOWN',
        fixedVersions: fixesForPackage(advisory.affectedJson, pkg.ecosystem, pkg.name), url: advisory.url };
    })).filter(item => {
      const ecosystemMatches = this.ecosystem() === 'ALL' || item.ecosystem.toUpperCase() === this.ecosystem();
      const severityMatches = this.severity() === 'ALL' || item.severity.toUpperCase() === this.severity();
      return ecosystemMatches && severityMatches && ['CRITICAL','HIGH','MEDIUM','LOW'].includes(item.severity.toUpperCase());
    });
  }
  packagesWithAdvisories(): number { return new Set(this.reportIssues().map(item => `${item.ecosystem}/${item.packageName}`)).size; }
  affectedVersions(): number { return new Set(this.reportIssues().map(item => `${item.ecosystem}/${item.packageName}/${item.version}`)).size; }
  issueCount(): number { return this.reportIssues().length; }
  assessmentPartial(): boolean { return this.assessments().some(item => !['MATCHED','NO_KNOWN_ADVISORIES'].includes(item.status)); }
  severityCount(severity: string): number { return this.reportIssues().filter(item => item.severity.toUpperCase() === severity).length; }
  executiveIssues(): ExecutiveIssue[] {
    return this.reportIssues().map(item => ({ ...item, whyChange: this.executiveReason(item.severity), nextStep: this.executiveNextStep(item) }));
  }
  executiveReason(severity: string): string {
    switch (severity.toUpperCase()) {
      case 'CRITICAL': return 'OSV clasifica el aviso como crítico, el nivel de prioridad más alto. Conviene atenderlo primero.';
      case 'HIGH': return 'OSV clasifica el aviso como alto. Conviene planificar su corrección pronto.';
      case 'MEDIUM': return 'OSV reporta un impacto medio. Programa la corrección y revisa si el uso del paquete aumenta la exposición.';
      default: return 'OSV reporta un impacto bajo. Incluye la corrección en el mantenimiento y conserva el seguimiento.';
    }
  }
  executiveNextStep(item: ReportIssue): string {
    return item.fixedVersions.length
      ? 'Actualizar a la versión corregida y validar la aplicación.'
      : 'Revisar el aviso oficial y acordar una corrección o mitigación.';
  }

  private readPreview(repo: Repository, done: () => void, failed: (error: HttpErrorResponse) => void): void {
    const query = new URLSearchParams({ repositoryId: repo.id, ecosystem: this.ecosystem(), severity: this.severity(), page: '0', size: '1' });
    this.api.get<CatalogPage>(`/api/organizations/${repo.organizationId}/reports/preview?${query}`).subscribe({ next: done, error: failed });
  }

  private reportPayload(repo: Repository): Record<string, unknown> {
    const issues = this.reportIssues();
    const executive = this.kind() === 'EXECUTIVE';
    return {
      title: executive ? 'Dependency Intelligence — Executive report' : 'Dependency Intelligence — Technical report',
      generatedAt: new Date().toISOString(), organization: repo.organizationName, repository: repo.fullName,
      source: repo.source, ecosystemFilter: this.ecosystem(), severityFilter: this.severity(),
      totals: { affectedPackages: this.packagesWithAdvisories(), affectedVersions: this.affectedVersions(), advisoryVersionRecords: issues.length,
        critical: this.severityCount('CRITICAL'), high: this.severityCount('HIGH'), medium: this.severityCount('MEDIUM'), low: this.severityCount('LOW') },
      partial: this.assessmentPartial(),
      coverageNote: this.assessmentPartial() ? 'Algunas versiones no tienen una consulta OSV completa o reciente; el informe incluye solo avisos con severidad conocida.' : null,
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
      ['Affected packages', totals.affectedPackages],
      ['Affected versions', totals.affectedVersions],
      ['Critical', totals.critical], ['High', totals.high], ['Medium', totals.medium], ['Low', totals.low],
      ['Executive summary', payload['executiveSummary']],
      [],
      this.kind() === 'EXECUTIVE'
        ? ['Ecosystem','Affected package','Affected version','Severity','Problem reported','Why change it','Next step','Advisory','OSV source']
        : ['Ecosystem','Affected package','Affected version','Severity','Advisory','Problem summary','Technical details','Fixed versions','OSV source'],
      ...rows.map(row => this.kind() === 'EXECUTIVE'
        ? [row.ecosystem,row.packageName,row.version,row.severity,row.summary,(row as ExecutiveIssue).whyChange,`${(row as ExecutiveIssue).nextStep} ${(row as ExecutiveIssue).fixedVersions.join(', ')}`.trim(),row.advisoryId,row.url]
        : [row.ecosystem,row.packageName,row.version,row.severity,row.advisoryId,row.summary,row.details,row.fixedVersions.join(', '),row.url]),
    ].map(row => row.map(quote).join(',')).join('\r\n');
  }
  private download(content: string, type: string, filename: string): void {
    const url = URL.createObjectURL(new Blob([content], { type }));
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click(); URL.revokeObjectURL(url);
  }
}
