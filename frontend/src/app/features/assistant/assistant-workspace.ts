import { Component, inject, Input, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { Organization, SessionApiService, accessError } from '../../core/session-api.service';
import { PreferencesService } from '../../core/preferences.service';
import { DiIcon } from '../../shared/di-icon';

interface Answer { conversationId: string; messageId: string; answer: string; proposedRepairPrompt: string | null; citations: Citation[]; status: string; model: string | null; }
interface Citation { documentId: string; chunkId: string; documentName: string; filename: string; page: number | null; section: string | null; excerpt: string; }
interface Turn { role: 'USER' | 'ASSISTANT'; content: string; citations?: Citation[]; proposedRepairPrompt?: string | null; }

@Component({ selector: 'app-assistant-workspace', imports: [DiIcon], templateUrl: './assistant-workspace.html', styleUrl: './assistant-workspace.scss' })
export class AssistantWorkspace {
  @Input() compact = false;
  private readonly api = inject(SessionApiService);
  readonly preferences = inject(PreferencesService);
  readonly organizations = signal<Organization[]>([]);
  readonly organizationId = signal('');
  readonly question = signal('');
  readonly conversationId = signal<string | null>(null);
  readonly turns = signal<Turn[]>([]);
  readonly pending = signal(false);
  readonly indexing = signal(false);
  readonly error = signal('');
  readonly notice = signal('');

  constructor() {
    this.api.get<Organization[]>('/api/organizations').subscribe({
      next: organizations => { this.organizations.set(organizations); this.organizationId.set(organizations[0]?.id ?? ''); },
      error: error => this.error.set(accessError(error)),
    });
  }
  changeOrganization(event: Event): void { this.organizationId.set((event.target as HTMLSelectElement).value); this.reset(); }
  reset(): void { this.conversationId.set(null); this.turns.set([]); this.error.set(''); this.notice.set(''); }
  indexDocuments(): void {
    const org = this.organizationId(); if (!org || this.indexing()) return;
    this.indexing.set(true); this.error.set(''); this.notice.set('');
    this.indexPendingBatch(org, 0, 0, 0);
  }
  private indexPendingBatch(org: string, indexed: number, failed: number, batches: number): void {
    this.api.post<{ indexed: number; failed: number; morePending: boolean }>(`/api/organizations/${org}/assistant/index`, {}).subscribe({
      next: result => {
        const totalIndexed = indexed + result.indexed, totalFailed = failed + result.failed, totalBatches = batches + 1;
        const english = this.preferences.language() === 'en';
        if (result.morePending && result.indexed > 0 && totalBatches < 100) {
          this.notice.set(english ? `Preparing passages: ${totalIndexed} ready, ${totalFailed} failed.` : `Preparando fragmentos: ${totalIndexed} listos, ${totalFailed} con error.`);
          this.indexPendingBatch(org, totalIndexed, totalFailed, totalBatches);
          return;
        }
        this.indexing.set(false);
        const stopped = result.morePending;
        this.notice.set(english
          ? `Prepared ${totalIndexed} passages; ${totalFailed} failed.${stopped ? (totalFailed ? ' Some remain after failures; check extraction/provider availability and retry.' : ' The per-run limit was reached; select this action again to continue.') : ''}`
          : `Se prepararon ${totalIndexed} fragmentos; ${totalFailed} con error.${stopped ? (totalFailed ? ' Quedan fragmentos tras errores; revisa la extracción o el proveedor y vuelve a intentarlo.' : ' Se alcanzó el límite por ejecución; pulsa de nuevo para continuar.') : ''}`);
      },
      error: error => { this.indexing.set(false); this.error.set(accessError(error)); },
    });
  }
  ask(event: Event): void {
    event.preventDefault();
    const org = this.organizationId(), question = this.question().trim(); if (!org || !question || this.pending()) return;
    this.turns.update(turns => [...turns, { role: 'USER', content: question }]);
    this.question.set(''); this.pending.set(true); this.error.set(''); this.notice.set('');
    this.api.post<Answer>(`/api/organizations/${org}/assistant/ask`, { conversationId: this.conversationId(), question })
      .pipe(finalize(() => this.pending.set(false))).subscribe({
        next: result => {
          this.conversationId.set(result.conversationId);
          const answer = result.status === 'NO_EVIDENCE' && this.preferences.language() === 'en'
            ? 'I could not find authorized document evidence to answer this question. Link relevant documentation or ask a more specific question.'
            : result.answer;
          this.turns.update(turns => [...turns, { role: 'ASSISTANT', content: answer, citations: result.citations, proposedRepairPrompt: result.proposedRepairPrompt }]);
        },
        error: error => { this.turns.update(turns => turns.slice(0, -1)); this.question.set(question); this.error.set(accessError(error)); },
      });
  }
  questionKeydown(event: KeyboardEvent):void {
    if(event.key!=='Enter'||event.shiftKey||event.isComposing)return;
    this.ask(event);
  }
  copyRepairPrompt(prompt: string): void {
    navigator.clipboard.writeText(prompt).then(() => this.notice.set(this.preferences.language() === 'en' ? 'Repair prompt copied.' : 'Prompt de reparación copiado.'))
      .catch(() => this.error.set(this.preferences.language() === 'en' ? 'Clipboard access is unavailable in this browser.' : 'El navegador no permitió copiar al portapapeles.'));
  }
}
