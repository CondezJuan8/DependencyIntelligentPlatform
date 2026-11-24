import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { accessError, SessionApiService } from '../../core/session-api.service';
import { TranslatePipe } from '../../core/translate.pipe';

interface Repository { id: string; organizationId: string; organizationName: string; fullName: string; source: string; }
interface PackageSummary { key: { ecosystem: string; namespace: string; name: string }; versions: number; repositories: number; occurrences: number; withAdvisories: number; severity: string | null; pending: number; }
interface CatalogPage { packages: PackageSummary[]; totalPackages: number; totalVersions: number; }
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
  readonly packages = signal<PackageSummary[]>([]);
  readonly totalPackages = signal(0);
  readonly totalVersions = signal(0);
  readonly partial = signal(false);
  readonly loading = signal(true);
  readonly exporting = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly generatedAt = new Date();
  private readonly maxReportPackages = 5000;
  private readonly pageSize = 100;

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
    this.loading.set(true); this.error.set(''); this.notice.set(''); this.packages.set([]); this.partial.set(false);
    this.readPages(repo, 'preview', 0, [], first => {
      this.totalPackages.set(first.totalPackages); this.totalVersions.set(first.totalVersions);
      this.packages.set(first.packages); this.partial.set(first.totalPackages > first.packages.length);
      this.loading.set(false);
    }, failure => { this.loading.set(false); this.error.set(accessError(failure)); });
  }

  export(format: Format): void {
    const repo = this.currentRepository(); if (!repo || this.exporting()) return;
    this.exporting.set(true); this.error.set(''); this.notice.set('');
    this.readPages(repo, 'export-data', 0, [], all => {
      try {
        const payload = this.reportPayload(repo, all);
        if (format === 'PDF') { this.printReport(); this.notice.set('Usa “Guardar como PDF” en el diálogo de impresión.'); }
        else if (format === 'JSON') this.download(JSON.stringify(payload, null, 2), 'application/json;charset=utf-8', 'dependency-intelligence-report.json');
        else this.download(this.csv(payload), 'text/csv;charset=utf-8', 'dependency-intelligence-report.csv');
      } catch { this.error.set('No se pudo construir el archivo del reporte. Vuelve a intentarlo.'); }
      finally { this.exporting.set(false); }
    }, failure => { this.exporting.set(false); this.error.set(accessError(failure)); });
  }

  currentRepository(): Repository | null { return this.repositories().find(item => item.id === this.repositoryId()) ?? null; }
  reportPackages(): PackageSummary[] {
    const data = this.packages();
    return this.kind() === 'EXECUTIVE'
      ? data.filter(item => item.withAdvisories > 0 || item.pending > 0 || ['CRITICAL','HIGH'].includes(item.severity ?? ''))
      : data;
  }
  packagesWithAdvisories(): number { return this.packages().filter(item => item.withAdvisories > 0).length; }
  pendingPackages(): number { return this.packages().filter(item => item.pending > 0).length; }

  private readPages(repo: Repository, endpoint: 'preview' | 'export-data', page: number, collected: PackageSummary[], done: (catalog: CatalogPage) => void, failed: (error: HttpErrorResponse) => void): void {
    const query = new URLSearchParams({ repositoryId: repo.id, ecosystem: this.ecosystem(), severity: this.severity(), page: String(page), size: String(this.pageSize) });
    this.api.get<CatalogPage>(`/api/organizations/${repo.organizationId}/reports/${endpoint}?${query}`).subscribe({
      next: result => {
        const all = [...collected, ...result.packages];
        const isCapped = all.length >= this.maxReportPackages;
        if (page === 0 && endpoint === 'preview') { this.totalPackages.set(result.totalPackages); this.totalVersions.set(result.totalVersions); }
        if (!isCapped && all.length < result.totalPackages) this.readPages(repo, endpoint, page + 1, all, done, failed);
        else done({ packages: all.slice(0, this.maxReportPackages), totalPackages: result.totalPackages, totalVersions: result.totalVersions });
      }, error: failed,
    });
  }

  private reportPayload(repo: Repository, catalog: CatalogPage): Record<string, unknown> {
    return {
      title: this.kind() === 'EXECUTIVE' ? 'Dependency Intelligence — Executive report' : 'Dependency Intelligence — Technical report',
      generatedAt: new Date().toISOString(), organization: repo.organizationName, repository: repo.fullName,
      source: repo.source, ecosystemFilter: this.ecosystem(), severityFilter: this.severity(),
      totals: { packages: catalog.totalPackages, versions: catalog.totalVersions, packagesWithAdvisories: catalog.packages.filter(item => item.withAdvisories > 0).length, packagesPendingAssessment: catalog.packages.filter(item => item.pending > 0).length },
      partial: catalog.totalPackages > catalog.packages.length,
      coverageNote: catalog.totalPackages > catalog.packages.length ? `The preview includes the first ${catalog.packages.length} of ${catalog.totalPackages} packages.` : null,
      packages: this.kind() === 'EXECUTIVE' ? catalog.packages.filter(item => item.withAdvisories > 0 || item.pending > 0 || ['CRITICAL','HIGH'].includes(item.severity ?? '')) : catalog.packages,
      limitations: ['Risk and advisory totals reflect stored OSV assessments. Missing, stale or failed checks are not proof that a package is safe.'],
    };
  }
  private csv(payload: Record<string, unknown>): string {
    const rows = payload['packages'] as PackageSummary[];
    const quote = (value: unknown) => `"${String(value ?? '').replaceAll('"', '""')}"`;
    return '\uFEFF' + [
      ['Ecosystem','Namespace','Package','Versions','Occurrences','Versions with advisories','Highest severity','Pending assessment'],
      ...rows.map(row => [row.key.ecosystem,row.key.namespace,row.key.name,row.versions,row.occurrences,row.withAdvisories,row.severity,row.pending]),
    ].map(row => row.map(quote).join(',')).join('\r\n');
  }
  private download(content: string, type: string, filename: string): void {
    const url = URL.createObjectURL(new Blob([content], { type }));
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click(); URL.revokeObjectURL(url);
  }
}
