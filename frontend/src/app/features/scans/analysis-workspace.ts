import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, output, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { accessError, SessionApiService } from '../../core/session-api.service';
import { TranslatePipe } from '../../core/translate.pipe';

interface Repository { id: string; organizationId: string; organizationName: string; fullName: string; source: string; }
interface AnalysisResult { ecosystem: string; status: 'COMPLETED' | 'NO_MANIFEST' | 'FAILED'; message: string | null; scan: { scanId: string; dependencies: unknown[] } | null; }
interface ScanHistory {
  id: string; repositoryId: string; repositoryName: string; gitReference: string | null; commitSha: string | null;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'; requestedAt: string; startedAt: string | null; finishedAt: string | null;
  failureReason: string | null; origin: string; scannerName: string | null; scannerVersion: string | null;
  sourceSha256: string | null; manifestCount: number; dependencyCount: number; unresolvedCount: number;
  coverageStatus: 'COMPLETE' | 'PARTIAL'; coverageMessage: string | null;
}

@Component({
  selector: 'app-analysis-workspace',
  imports: [DatePipe, TranslatePipe],
  templateUrl: './analysis-workspace.html',
  styleUrl: './analysis-workspace.scss',
})
export class AnalysisWorkspace implements OnInit {
  private readonly api = inject(SessionApiService);
  readonly openInventory = output<string>();
  readonly repositories = signal<Repository[]>([]);
  readonly selected = signal<Repository | null>(null);
  readonly history = signal<ScanHistory[]>([]);
  readonly loading = signal(true);
  readonly loadingHistory = signal(false);
  readonly running = signal(false);
  readonly assessing = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly latestResult = signal<AnalysisResult[] | null>(null);

  ngOnInit(): void {
    this.api.get<Repository[]>('/api/account/workspace').pipe(finalize(() => this.loading.set(false))).subscribe({
      next: repos => {
        this.repositories.set(repos);
        const wanted = new URLSearchParams(window.location.search).get('repositoryId');
        const repo = repos.find(item => item.id === wanted) ?? repos[0] ?? null;
        this.select(repo);
      },
      error: failure => this.error.set(accessError(failure)),
    });
  }

  select(repository: Repository | null): void {
    this.selected.set(repository);
    this.latestResult.set(null);
    this.error.set('');
    this.notice.set('');
    if (!repository) { this.history.set([]); return; }
    this.refreshHistory(repository);
  }

  selectFromControl(event: Event): void {
    const id = (event.target as HTMLSelectElement).value;
    this.select(this.repositories().find(repo => repo.id === id) ?? null);
  }

  refresh(): void { const repo = this.selected(); if (repo) this.refreshHistory(repo); }

  runAnalysis(): void {
    const repo = this.selected();
    if (!repo || this.running()) return;
    this.running.set(true);
    this.error.set('');
    this.notice.set('');
    this.latestResult.set(null);
    this.api.post<AnalysisResult[]>(this.endpoint(repo, 'dependency-analysis'), {}).pipe(
      finalize(() => this.running.set(false)),
    ).subscribe({
      next: results => {
        this.latestResult.set(results);
        const completed = results.filter(result => result.status === 'COMPLETED');
        const failed = results.filter(result => result.status === 'FAILED');
        this.notice.set(failed.length
          ? 'El análisis terminó con errores en algunos ecosistemas. Revisa el resultado y el historial.'
          : completed.length
            ? 'Análisis terminado. El inventario se actualizó con la fuente y el commit observados.'
            : 'No se encontró un manifiesto Maven ni un lockfile npm compatible en este repositorio.');
        this.refreshHistory(repo);
        const scanIds = completed.flatMap(result => result.scan?.dependencies.length ? [result.scan.scanId] : []);
        if (scanIds.length) this.assess(repo, scanIds);
      },
      error: failure => this.error.set(accessError(failure)),
    });
  }

  private assess(repo: Repository, scanIds: string[]): void {
    this.assessing.set(true);
    this.api.post(this.endpoint(repo, 'vulnerability-analysis'), { scanIds }).pipe(
      finalize(() => this.assessing.set(false)),
    ).subscribe({
      next: () => this.notice.set('Análisis terminado y evaluación OSV solicitada. Scanner y OSV se ejecutan como etapas distintas.'),
      error: failure => this.notice.set(`El inventario quedó guardado, pero OSV no completó la evaluación: ${accessError(failure)}`),
    });
  }

  private refreshHistory(repo: Repository): void {
    this.loadingHistory.set(true);
    this.api.get<ScanHistory[]>(this.endpoint(repo, 'scans')).pipe(
      finalize(() => this.loadingHistory.set(false)),
    ).subscribe({
      next: items => this.history.set(items),
      error: failure => this.error.set(accessError(failure)),
    });
  }

  private endpoint(repo: Repository, suffix: string): string {
    return `/api/organizations/${repo.organizationId}/repositories/${repo.id}/${suffix}`;
  }

  stateLabel(item: ScanHistory): string {
    if (item.status === 'COMPLETED' && item.coverageStatus === 'PARTIAL') return 'Completo · cobertura parcial';
    return ({ PENDING: 'Pendiente', RUNNING: 'En curso', COMPLETED: 'Completado', FAILED: 'Fallido' })[item.status];
  }

  sourceLabel(item: ScanHistory): string {
    if (item.origin === 'LOCAL_UPLOAD_SCAN') return 'Carpeta local';
    if (item.origin === 'SBOM_IMPORT') return 'SBOM importado';
    return 'Repositorio conectado';
  }

  duration(item: ScanHistory): string {
    if (!item.startedAt || !item.finishedAt) return '—';
    const seconds = Math.max(0, Math.round((Date.parse(item.finishedAt) - Date.parse(item.startedAt)) / 1000));
    return seconds < 60 ? `${seconds} s` : `${Math.floor(seconds / 60)} min ${seconds % 60} s`;
  }
}
