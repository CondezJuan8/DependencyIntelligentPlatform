import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { finalize, switchMap } from 'rxjs';
import { accessError, SessionApiService } from '../../core/session-api.service';
import { TranslatePipe } from '../../core/translate.pipe';
import { fixesForPackage } from '../inventory/inventory-evidence';
import { ReportIssue, ReportSeverity, reportIssue, reportSeverities, severityLabels } from './report-content';

interface Repository { id: string; organizationId: string; organizationName: string; fullName: string; source: string; }
interface CatalogPage { packages: unknown[]; totalPackages: number; totalVersions: number; }
interface Advisory { id: string; summary: string | null; details: string | null; severity: string; url: string; affectedJson: string; }
interface VersionAssessment {
  packageVersion: { ecosystem: string; namespace: string | null; name: string; version: string };
  status: string; checkedAt: string | null; advisories: Advisory[];
}
interface InventoryAssessment { generatedAt: string; assessments: VersionAssessment[]; }
type ReportKind = 'EXECUTIVE' | 'TECHNICAL';
type Format = 'JSON' | 'CSV' | 'PDF';

@Component({ selector: 'app-reports-workspace', imports: [DatePipe, TranslatePipe],
  templateUrl: './reports-workspace.html', styleUrl: './reports-workspace.scss' })
export class ReportsWorkspace implements OnInit {
  private readonly api = inject(SessionApiService);
  readonly repositories = signal<Repository[]>([]);
  readonly repositoryId = signal('');
  readonly ecosystem = signal('ALL');
  readonly severity = signal<'ALL' | ReportSeverity>('ALL');
  readonly kind = signal<ReportKind>('EXECUTIVE');
  readonly assessments = signal<VersionAssessment[]>([]);
  readonly loading = signal(true);
  readonly exporting = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly exportError = signal('');
  readonly generatedAt = signal<Date | null>(null);
  readonly assessmentDate = signal<string | null>(null);
  readonly severityLabels = severityLabels;
  readonly reportSeverities = reportSeverities;
  private previewRequest = 0;

  readonly issues = computed(() => {
    const rows = this.assessments().filter(item => ['MATCHED', 'STALE'].includes(item.status))
      .sort((a, b) => (b.checkedAt ?? '').localeCompare(a.checkedAt ?? ''))
      .flatMap(item => item.advisories.map(advisory => {
        const level = advisory.severity?.toUpperCase() as ReportSeverity;
        if (!reportSeverities.includes(level)) return null;
        const pkg = item.packageVersion;
        const ecosystem = pkg.ecosystem.toUpperCase();
        if (this.ecosystem() !== 'ALL' && ecosystem !== this.ecosystem()) return null;
        if (this.severity() !== 'ALL' && level !== this.severity()) return null;
        const packageName = ecosystem === 'MAVEN' && pkg.namespace ? `${pkg.namespace}:${pkg.name}`
          : pkg.namespace ? `${pkg.namespace}/${pkg.name}` : pkg.name;
        return reportIssue({ ecosystem, dependency: pkg.name, packageName, version: pkg.version,
          advisoryId: advisory.id, severity: level, summary: advisory.summary, details: advisory.details,
          fixedVersions: fixesForPackage(advisory.affectedJson, pkg.ecosystem, packageName),
          url: advisory.url, assessedAt: item.checkedAt });
      }).filter((issue): issue is ReportIssue => issue !== null));
    const distinct = new Map<string, ReportIssue>();
    for (const issue of rows) {
      const key = `${issue.ecosystem}/${issue.packageName}/${issue.version}/${issue.advisoryId}`;
      if (!distinct.has(key)) distinct.set(key, issue);
    }
    return [...distinct.values()].sort((a, b) => reportSeverities.indexOf(a.severity) - reportSeverities.indexOf(b.severity)
      || a.packageName.localeCompare(b.packageName) || a.version.localeCompare(b.version));
  });

  ngOnInit(): void {
    this.api.get<Repository[]>('/api/account/workspace').subscribe({
      next: repos => {
        this.repositories.set(repos);
        this.repositoryId.set((repos.find(repo => repo.id === new URLSearchParams(window.location.search).get('repositoryId')) ?? repos[0])?.id ?? '');
        if (repos.length) this.loadPreview(); else this.loading.set(false);
      },
      error: failure => { this.loading.set(false); this.error.set(accessError(failure)); },
    });
  }
  changeRepository(event: Event): void { this.repositoryId.set((event.target as HTMLSelectElement).value); this.loadPreview(); }
  changeFilter(event: Event, filter: 'ecosystem' | 'severity'): void {
    const value = (event.target as HTMLSelectElement).value;
    if (filter === 'ecosystem') this.ecosystem.set(value);
    else this.severity.set(value as 'ALL' | ReportSeverity);
    this.loadPreview();
  }
  setKind(kind: ReportKind): void { this.kind.set(kind); }
  currentRepository(): Repository | null { return this.repositories().find(item => item.id === this.repositoryId()) ?? null; }
  reportIssues(): ReportIssue[] { return this.issues(); }
  issuesFor(level: ReportSeverity): ReportIssue[] { return this.issues().filter(issue => issue.severity === level); }
  issueCount(): number { return this.issues().length; }
  severityCount(level: ReportSeverity): number { return this.issuesFor(level).length; }
  selectedSeverityLabel(): string { return this.severity() === 'ALL' ? 'Media a crítica' : severityLabels[this.severity() as ReportSeverity]; }
  packagesWithAdvisories(): number { return new Set(this.issues().map(item => `${item.ecosystem}/${item.packageName}`)).size; }
  affectedVersions(): number { return new Set(this.issues().map(item => `${item.ecosystem}/${item.packageName}/${item.version}`)).size; }
  coverageNote(): string | null {
    const scoped = this.assessments().filter(item => this.ecosystem() === 'ALL' || item.packageVersion.ecosystem.toUpperCase() === this.ecosystem());
    if (!scoped.length) return 'No hay evaluaciones OSV guardadas para el alcance seleccionado.';
    return scoped.some(item => !['MATCHED', 'NO_KNOWN_ADVISORIES'].includes(item.status))
      ? 'Algunas versiones no tienen una consulta OSV completa o reciente. El informe puede omitir avisos nuevos.' : null;
  }
  loadPreview(): void {
    const repo = this.currentRepository(); if (!repo) return;
    const request = ++this.previewRequest;
    this.loading.set(true); this.error.set(''); this.exportError.set(''); this.notice.set('');
    this.assessments.set([]); this.generatedAt.set(null); this.assessmentDate.set(null);
    this.api.get<CatalogPage>(this.reportEndpoint(repo, 'preview')).pipe(
      switchMap(() => this.api.get<InventoryAssessment>(`/api/organizations/${repo.organizationId}/repositories/${repo.id}/vulnerability-analysis`)),
      finalize(() => { if (request === this.previewRequest) this.loading.set(false); }),
    ).subscribe({
      next: assessment => {
        if (request !== this.previewRequest) return;
        this.assessments.set(assessment.assessments);
        this.generatedAt.set(new Date()); this.assessmentDate.set(assessment.generatedAt);
      },
      error: failure => { if (request === this.previewRequest) this.error.set(accessError(failure)); },
    });
  }
  export(format: Format): void {
    const repo = this.currentRepository();
    if (!repo || this.exporting() || !this.generatedAt()) return;
    const preview = this.previewRequest;
    const kind = this.kind();
    this.exporting.set(true); this.exportError.set(''); this.notice.set('');
    this.api.get<CatalogPage>(this.reportEndpoint(repo, 'export-data')).subscribe({
      next: async () => {
        if (preview !== this.previewRequest || kind !== this.kind()) { this.exporting.set(false); return; }
        try {
          const payload = this.reportPayload(repo);
          if (format === 'PDF') {
            const { downloadReportPdf } = await import('./report-pdf');
            const date = (value: Date) => new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' }).format(value);
            downloadReportPdf({ kind, organization: repo.organizationName, repository: repo.fullName,
              generatedAt: date(this.generatedAt()!), assessmentDate: this.assessmentDate() ? date(new Date(this.assessmentDate()!)) : null,
              coverageNote: this.coverageNote(), issues: this.issues() });
          } else if (format === 'JSON') {
            this.download(JSON.stringify(payload, null, 2), 'application/json;charset=utf-8', 'dependency-intelligence-report.json');
          } else this.download(this.csv(payload), 'text/csv;charset=utf-8', 'dependency-intelligence-report.csv');
          this.notice.set('Reporte descargado.');
        } catch { this.exportError.set('No se pudo construir el archivo del reporte. Vuelve a intentarlo.'); }
        finally { this.exporting.set(false); }
      },
      error: failure => { this.exporting.set(false); if (preview === this.previewRequest) this.exportError.set(accessError(failure)); },
    });
  }
  private reportEndpoint(repo: Repository, action: 'preview' | 'export-data'): string {
    const query = new URLSearchParams({ repositoryId: repo.id, ecosystem: this.ecosystem(), severity: this.severity(), page: '0', size: '1' });
    return `/api/organizations/${repo.organizationId}/reports/${action}?${query}`;
  }
  private reportPayload(repo: Repository): Record<string, unknown> {
    return { title: this.kind() === 'EXECUTIVE' ? 'Informe ejecutivo de vulnerabilidades' : 'Informe técnico de vulnerabilidades',
      generatedAt: this.generatedAt()?.toISOString(), assessmentDate: this.assessmentDate(),
      organization: repo.organizationName, repository: repo.fullName, source: repo.source,
      ecosystemFilter: this.ecosystem(), severityFilter: this.severity(), minimumSeverity: 'MEDIUM',
      totals: { affectedPackages: this.packagesWithAdvisories(), affectedVersions: this.affectedVersions(),
        advisories: this.issueCount(), critical: this.severityCount('CRITICAL'), high: this.severityCount('HIGH'), medium: this.severityCount('MEDIUM') },
      coverageNote: this.coverageNote(), findings: this.issues() };
  }
  private csv(payload: Record<string, unknown>): string {
    const quote = (value: unknown) => {
      const text = String(value ?? '');
      const safe = /^[=+\-@\t\r]/.test(text) ? `'${text}` : text;
      return `"${safe.replaceAll('"', '""')}"`;
    };
    const rows: unknown[][] = [
      ['Tipo', payload['title']], ['Organización', payload['organization']], ['Repositorio', payload['repository']],
      ['Generado', payload['generatedAt']], ['Evidencia OSV', payload['assessmentDate']], ['Cobertura', payload['coverageNote']], [],
      this.kind() === 'EXECUTIVE'
        ? ['Severidad', 'Dependencia', 'Paquete', 'Versión', 'Qué puede causar', 'Por qué cambiarla', 'Aviso', 'Fuente OSV']
        : ['Severidad', 'Dependencia', 'Paquete', 'Versión', 'Qué hace', 'Descripción OSV', 'Versiones corregidas', 'Aviso', 'Fuente OSV'],
      ...this.issues().map(item => this.kind() === 'EXECUTIVE'
        ? [severityLabels[item.severity], item.dependency, item.packageName, item.version, item.businessImpact, item.whyChange, item.advisoryId, item.url]
        : [severityLabels[item.severity], item.dependency, item.packageName, item.version, item.technicalEffect,
          item.technicalDescription, item.fixedVersions.join(', '), item.advisoryId, item.url]),
    ];
    return '\uFEFF' + rows.map(row => row.map(quote).join(',')).join('\r\n');
  }
  private download(content: string, type: string, filename: string): void {
    const url = URL.createObjectURL(new Blob([content], { type }));
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
