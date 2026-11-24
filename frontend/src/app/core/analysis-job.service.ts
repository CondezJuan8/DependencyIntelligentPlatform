import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, forkJoin, of, timer } from 'rxjs';
import { expand, map, switchMap, takeWhile } from 'rxjs/operators';
import { SessionApiService } from './session-api.service';

export interface AnalysisRepository { id: string; organizationId: string; }
export interface AnalysisTicket { jobId: string; status: string; }
export interface AnalysisJobStatus {
  jobId: string; status: 'PENDING' | 'RUNNING' | 'RETRYING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  requestedAt: string; startedAt: string | null; finishedAt: string | null; errorCode: string | null;
  completedStages: string[];
  scans: { ecosystem: string; scanId: string; status: 'COMPLETED' | 'NO_MANIFEST' | 'FAILED';
    manifestCount: number; dependencyCount: number; unresolvedCount: number; coverageStatus: string | null;
    coverageMessage: string | null; failureReason: string | null; }[];
}
export interface AnalysisDependency {
  manifestPath: string; coordinates: { namespace: string | null; name: string; version: string };
  scope: string; dependencyType: string;
}
export interface AnalysisScan {
  scanId: string; source: string; sourceSha256: string; commitSha: string | null; analyzedAt: string;
  manifestCount: number; unresolvedCount: number; coverageStatus: 'COMPLETE' | 'PARTIAL';
  coverageMessage: string | null; dependencies: AnalysisDependency[];
}
export interface AnalysisResult {
  ecosystem: string; status: 'COMPLETED' | 'NO_MANIFEST' | 'FAILED'; message: string | null;
  scan: AnalysisScan | null;
}

@Injectable({ providedIn: 'root' })
export class AnalysisJobService {
  private readonly api = inject(SessionApiService);

  request(repository: AnalysisRepository): Observable<AnalysisTicket> {
    const key = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    return this.api.post<AnalysisTicket>(this.endpoint(repository), {}, { 'Idempotency-Key': key });
  }

  active(repository: AnalysisRepository): Observable<AnalysisJobStatus | null> {
    return this.api.get<AnalysisJobStatus | null>(`${this.endpoint(repository)}/active`);
  }

  watch(repository: AnalysisRepository, jobId: string): Observable<AnalysisJobStatus> {
    const url = `${this.endpoint(repository)}/${jobId}`;
    const terminal = (status: AnalysisJobStatus) => ['COMPLETED', 'FAILED', 'CANCELLED'].includes(status.status);
    return this.api.get<AnalysisJobStatus>(url).pipe(
      expand(status => terminal(status) ? EMPTY : timer(1200).pipe(switchMap(() => this.api.get<AnalysisJobStatus>(url)))),
      takeWhile(status => !terminal(status), true),
    );
  }

  cancel(repository: AnalysisRepository, jobId: string): Observable<AnalysisJobStatus> {
    return this.api.delete<AnalysisJobStatus>(`${this.endpoint(repository)}/${jobId}`);
  }

  results(repository: AnalysisRepository, job: AnalysisJobStatus): Observable<AnalysisResult[]> {
    const results = ['MAVEN', 'NPM'].map(ecosystem => {
      const scan = job.scans.find(item => item.ecosystem === ecosystem);
      if (!scan) return of<AnalysisResult>({ ecosystem, status: 'FAILED', message: job.errorCode ?? 'El análisis no dejó un resultado para este ecosistema.', scan: null });
      if (scan.status === 'FAILED') return of<AnalysisResult>({ ecosystem, status: 'FAILED', message: scan.failureReason ?? 'El análisis falló para este ecosistema.', scan: null });
      const endpoint = ecosystem === 'MAVEN' ? 'maven-analysis' : 'npm-analysis';
      const query = new URLSearchParams({ scanId: scan.scanId });
      return this.api.get<AnalysisResult['scan']>(`/api/organizations/${repository.organizationId}/repositories/${repository.id}/${endpoint}?${query}`)
        .pipe(map(found => ({ ecosystem, status: scan.status, message: scan.coverageMessage, scan: found })));
    });
    return forkJoin(results);
  }

  private endpoint(repository: AnalysisRepository): string {
    return `/api/organizations/${repository.organizationId}/repositories/${repository.id}/dependency-analysis`;
  }
}
