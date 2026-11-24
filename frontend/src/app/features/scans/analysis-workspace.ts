import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, output, signal } from '@angular/core';
import { finalize, Subscription } from 'rxjs';
import { AnalysisJobService, AnalysisJobStatus, AnalysisResult } from '../../core/analysis-job.service';
import { accessError, SessionApiService } from '../../core/session-api.service';
import { TranslatePipe } from '../../core/translate.pipe';

interface Repository { id: string; organizationId: string; organizationName: string; fullName: string; source: string; }
interface ScanHistory {
  id: string; repositoryId: string; repositoryName: string; gitReference: string | null; commitSha: string | null;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'; requestedAt: string; startedAt: string | null; finishedAt: string | null;
  failureReason: string | null; origin: string; scannerName: string | null; scannerVersion: string | null;
  sourceSha256: string | null; manifestCount: number; dependencyCount: number; unresolvedCount: number;
  coverageStatus: 'COMPLETE' | 'PARTIAL'; coverageMessage: string | null; displayName: string | null; sequenceNumber: number;
}
interface Comparison {
  base: { id: string; displayName: string | null; sequenceNumber: number; commitSha: string | null; completedAt: string | null; coverageStatus: string | null; coverageMessage: string | null };
  target: { id: string; displayName: string | null; sequenceNumber: number; commitSha: string | null; completedAt: string | null; coverageStatus: string | null; coverageMessage: string | null };
  added: number; removed: number; updated: number; unchanged: number;
  dependencies: { ecosystem: string; namespace: string; name: string; change: string; baseVersions: string[]; targetVersions: string[]; baseOccurrences: number; targetOccurrences: number; baseAdvisories: string[]; targetAdvisories: string[]; baseAssessment: string; targetAssessment: string }[];
  securityChanges: { advisoryId: string; change: string; packageName: string; sourceUrl: string }[];
  recommendations: { packageName: string; priority: string; explanation: string; evidenceIds: string[] }[];
}
type LoadingStepState = 'completed' | 'active' | 'waiting';
interface LoadingStep { title: string; detail: string; state: LoadingStepState; }

@Component({
  selector: 'app-analysis-workspace',
  imports: [DatePipe, TranslatePipe],
  templateUrl: './analysis-workspace.html',
  styleUrl: './analysis-workspace.scss',
})
export class AnalysisWorkspace implements OnInit {
  private readonly api = inject(SessionApiService);
  private readonly analysisJobs = inject(AnalysisJobService);
  private jobSubscription?: Subscription;
  readonly openInventory = output<string>();
  readonly repositories = signal<Repository[]>([]);
  readonly selected = signal<Repository | null>(null);
  readonly history = signal<ScanHistory[]>([]);
  readonly loading = signal(true);
  readonly loadingHistory = signal(false);
  readonly running = signal(false);
  readonly assessing = signal(false);
  readonly preparingResults = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly latestResult = signal<AnalysisResult[] | null>(null);
  readonly jobStatus = signal<AnalysisJobStatus | null>(null);
  readonly baseScanId = signal('');
  readonly targetScanId = signal('');
  readonly comparing = signal(false);
  readonly comparison = signal<Comparison | null>(null);
  readonly labelDrafts = signal<Record<string, string>>({});
  readonly savingLabelId = signal('');

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
    this.jobSubscription?.unsubscribe();
    this.jobSubscription = undefined;
    this.running.set(false);
    this.preparingResults.set(false);
    this.jobStatus.set(null);
    this.selected.set(repository);
    this.latestResult.set(null);
    this.comparison.set(null);
    this.error.set('');
    this.notice.set('');
    if (!repository) { this.history.set([]); return; }
    this.refreshHistory(repository);
    this.resumeActiveJob(repository);
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
    this.preparingResults.set(false);
    this.jobStatus.set(null);
    this.error.set('');
    this.notice.set('');
    this.latestResult.set(null);
    this.analysisJobs.request(repo).subscribe({
      next: ticket => this.watchJob(repo, ticket.jobId),
      error: failure => { this.running.set(false); this.error.set(accessError(failure)); },
    });
  }

  cancelAnalysis(): void {
    const repo = this.selected(), jobId = this.jobStatus()?.jobId;
    if (!repo || !jobId) return;
    this.analysisJobs.cancel(repo, jobId).subscribe({
      next: status => this.jobStatus.set(status),
      error: failure => this.error.set(accessError(failure)),
    });
  }

  analysisBusy(): boolean { return this.running() || this.preparingResults() || this.assessing(); }

  loadingHeadline(): string {
    if (this.assessing()) return 'Estamos comprobando las versiones con avisos OSV';
    if (this.preparingResults()) return 'Estamos reuniendo los resultados del análisis';
    const job = this.jobStatus();
    if (!job || job.status === 'PENDING' || job.status === 'RETRYING') return 'Estamos preparando el análisis';
    const stages = new Set(job.completedStages);
    if (!stages.has('SOURCE_PREPARED')) return 'Estamos leyendo la fuente del repositorio';
    if (!stages.has('MAVEN_FINISHED')) return 'Estamos identificando paquetes y versiones de Maven';
    if (!stages.has('NPM_FINISHED')) return 'Estamos identificando paquetes y versiones de npm';
    return 'Estamos reuniendo los resultados del análisis';
  }

  loadingExplanation(): string {
    if (this.assessing()) return 'El scanner terminó. Estamos consultando OSV para comprobar avisos conocidos para las versiones detectadas.';
    if (this.preparingResults()) return 'Estamos organizando los resultados de Maven y npm para actualizar el inventario de este repositorio.';
    const job = this.jobStatus();
    if (!job || job.status === 'PENDING' || job.status === 'RETRYING') return 'Tu solicitud está en cola. En cuanto el worker la tome, comenzará a leer la fuente autorizada.';
    const stages = new Set(job.completedStages);
    if (!stages.has('SOURCE_PREPARED')) return 'Estamos preparando la fuente del repositorio para revisar sus archivos de dependencias.';
    if (!stages.has('MAVEN_FINISHED')) return 'Estamos revisando los manifiestos Maven para reconocer los paquetes y versiones declarados.';
    if (!stages.has('NPM_FINISHED')) return 'Estamos revisando los archivos npm para reconocer los paquetes y versiones declarados.';
    return 'Los scanners terminaron. Estamos reuniendo los resultados antes de mostrártelos.';
  }

  loadingSteps(): LoadingStep[] {
    const job = this.jobStatus();
    const stages = new Set(job?.completedStages ?? []);
    const runningJob = !!job && ['RUNNING', 'PENDING', 'RETRYING'].includes(job.status);
    const sourceDone = stages.has('SOURCE_PREPARED');
    const mavenDone = stages.has('MAVEN_FINISHED');
    const npmDone = stages.has('NPM_FINISHED');
    const step = (title: string, detail: string, done: boolean, active: boolean): LoadingStep => ({
      title, detail, state: done ? 'completed' : active ? 'active' : 'waiting',
    });
    return [
      step('Leer la fuente del repositorio', 'Preparar los archivos autorizados para analizarlos.', sourceDone,
        runningJob && !sourceDone && job?.status === 'RUNNING'),
      step('Identificar dependencias Maven', 'Reconocer paquetes y versiones de los manifiestos Java.', mavenDone,
        runningJob && sourceDone && !mavenDone),
      step('Identificar dependencias npm', 'Reconocer paquetes y versiones de los archivos JavaScript.', npmDone,
        runningJob && mavenDone && !npmDone),
      step('Comprobar avisos conocidos', 'Consultar OSV para las versiones detectadas.', false, this.assessing()),
    ];
  }

  private resumeActiveJob(repo: Repository): void {
    this.analysisJobs.active(repo).subscribe({
      next: job => { if (job && this.selected()?.id === repo.id) this.watchJob(repo, job.jobId); },
      error: failure => this.error.set(accessError(failure)),
    });
  }

  private watchJob(repo: Repository, jobId: string): void {
    this.jobSubscription?.unsubscribe();
    this.running.set(true);
    this.jobSubscription = this.analysisJobs.watch(repo, jobId).pipe(finalize(() => {
      if (this.selected()?.id === repo.id) this.running.set(false);
    })).subscribe({
      next: status => {
        if (this.selected()?.id !== repo.id) return;
        this.jobStatus.set(status);
        if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(status.status)) this.finishJob(repo, status);
      },
      error: failure => { if (this.selected()?.id === repo.id) { this.running.set(false); this.error.set(accessError(failure)); } },
    });
  }

  private finishJob(repo: Repository, status: AnalysisJobStatus): void {
    this.preparingResults.set(true);
    this.analysisJobs.results(repo, status).subscribe({
      next: results => {
        if (this.selected()?.id !== repo.id) { this.preparingResults.set(false); return; }
        this.latestResult.set(results);
        this.refreshHistory(repo);
        const completed = results.filter(result => result.status === 'COMPLETED');
        const failed = results.filter(result => result.status === 'FAILED');
        this.notice.set(status.status === 'CANCELLED'
          ? 'El análisis se canceló. Los resultados completados siguen disponibles en el inventario.'
          : status.status === 'FAILED'
            ? `El trabajo no pudo completarse (${status.errorCode ?? 'error'}). Revisa el historial.`
            : failed.length
              ? 'El análisis terminó con errores en algunos ecosistemas. Revisa el resultado y el historial.'
              : completed.length
                ? 'Análisis terminado. El inventario se actualizó con la fuente y el commit observados.'
                : 'No se encontró un manifiesto Maven ni un lockfile npm compatible en este repositorio.');
        const scanIds = completed.flatMap(result => result.scan?.dependencies.length ? [result.scan.scanId] : []);
        if (scanIds.length) this.assess(repo, scanIds);
        this.preparingResults.set(false);
      },
      error: failure => { this.preparingResults.set(false); this.error.set(accessError(failure)); },
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
      next: items => {
        this.history.set(items);
        this.labelDrafts.set(Object.fromEntries(items.map(item => [item.id, item.displayName ?? ''])));
        const complete = items.filter(item => item.status === 'COMPLETED');
        if (!complete.some(item => item.id === this.targetScanId())) this.targetScanId.set(complete[0]?.id ?? '');
        const alternative = complete.find(item => item.id !== this.targetScanId());
        if (!complete.some(item => item.id === this.baseScanId()) || this.baseScanId() === this.targetScanId()) this.baseScanId.set(alternative?.id ?? '');
      },
      error: failure => this.error.set(accessError(failure)),
    });
  }

  compareScans(): void {
    const repo = this.selected(), base = this.baseScanId(), target = this.targetScanId();
    if (!repo || !base || !target || base === target || this.comparing()) return;
    this.comparing.set(true); this.error.set(''); this.comparison.set(null);
    const query = new URLSearchParams({ baseScanId: base, targetScanId: target });
    this.api.get<Comparison>(`${this.endpoint(repo, 'scans')}/compare?${query}`)
      .pipe(finalize(() => this.comparing.set(false)))
      .subscribe({ next: result => this.comparison.set(result), error: failure => this.error.set(accessError(failure)) });
  }

  saveScanLabel(item: ScanHistory): void {
    const repo = this.selected();
    if (!repo || this.savingLabelId()) return;
    this.savingLabelId.set(item.id);
    this.api.put<void>(`${this.endpoint(repo, 'scans')}/${item.id}/label`, {
      displayName: this.labelDrafts()[item.id]?.trim() || null,
    }).pipe(finalize(() => this.savingLabelId.set(''))).subscribe({
      next: () => { this.notice.set('Nombre del análisis guardado.'); this.refreshHistory(repo); },
      error: failure => this.error.set(accessError(failure)),
    });
  }

  updateLabelDraft(scanId: string, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.labelDrafts.update(drafts => ({ ...drafts, [scanId]: value }));
  }

  scanName(item: ScanHistory | null | undefined): string {
    if (!item) return '—';
    return `#${item.sequenceNumber} · ${item.displayName?.trim() || this.revisionLabel(item)}`;
  }

  comparisonScanName(point: Comparison['base']): string {
    const label = point.displayName?.trim() || (point.commitSha
      ? (point.commitSha.length > 14 ? `${point.commitSha.slice(0, 12)}…` : point.commitSha)
      : 'Fuente local');
    return `#${point.sequenceNumber} · ${label}`;
  }

  private revisionLabel(item: ScanHistory): string {
    const revision = item.commitSha || item.gitReference || 'Fuente local';
    return revision.length > 14 ? `${revision.slice(0, 12)}…` : revision;
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

  shortId(value: string): string { return value.length > 8 ? value.slice(0, 8) : value; }
  shortHash(value: string): string { return value.length > 16 ? `${value.slice(0, 8)}…${value.slice(-6)}` : value; }
  scanLabel(item: ScanHistory): string {
    const when = new Date(item.requestedAt).toLocaleString();
    return `${this.scanName(item)} · ${when}`;
  }
  completedScans(): ScanHistory[] { return this.history().filter(item => item.status === 'COMPLETED'); }
}
