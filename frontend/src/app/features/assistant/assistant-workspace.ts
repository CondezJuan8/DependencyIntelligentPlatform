import { Component, computed, inject, Input, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { Organization, SessionApiService, accessError } from '../../core/session-api.service';
import { PreferencesService } from '../../core/preferences.service';
import { DiIcon } from '../../shared/di-icon';

interface Answer { conversationId: string; messageId: string; answer: string; proposedRepairPrompt: string | null; citations: Citation[]; status: string; model: string | null; }
interface Repository { id: string; organizationId: string; fullName: string; }
interface Citation { documentId: string | null; versionId?: string | null; chunkId: string; documentName: string; filename: string; page: number | null; section: string | null; excerpt: string; sourceUrl?: string | null; }
interface Turn { role: 'USER' | 'ASSISTANT'; content: string; citations?: Citation[]; proposedRepairPrompt?: string | null; }
interface ChatSummary { id: string; title: string; updatedAt: string; }

@Component({ selector: 'app-assistant-workspace', imports: [DiIcon], templateUrl: './assistant-workspace.html', styleUrl: './assistant-workspace.scss' })
export class AssistantWorkspace {
  @Input() compact = false;
  private readonly api = inject(SessionApiService);
  readonly preferences = inject(PreferencesService);
  readonly organizations = signal<Organization[]>([]);
  readonly organizationId = signal('');
  readonly repositoryId = signal('');
  private readonly repositories = signal<Repository[]>([]);
  readonly visibleRepositories = computed(() => this.repositories().filter(repo => repo.organizationId === this.organizationId()));
  readonly question = signal('');
  readonly conversationId = signal<string | null>(null);
  readonly chats = signal<ChatSummary[]>([]);
  readonly turns = signal<Turn[]>([]);
  readonly pending = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly confirmingDelete = signal(false);

  constructor() {
    this.api.get<Organization[]>('/api/organizations').subscribe({
      next: organizations => { this.organizations.set(organizations); this.organizationId.set(organizations[0]?.id ?? ''); this.loadChats(); },
      error: error => this.error.set(accessError(error)),
    });
    this.api.get<Repository[]>('/api/account/workspace').subscribe({
      next: repositories => this.repositories.set(repositories),
      error: error => this.error.set(accessError(error)),
    });
  }
  changeOrganization(event: Event): void { this.organizationId.set((event.target as HTMLSelectElement).value); this.repositoryId.set(''); this.reset(); this.loadChats(); }
  changeRepository(event: Event): void {
    this.repositoryId.set((event.target as HTMLSelectElement).value);
    this.reset();
  }
  citationLink(citation: Citation): string {
    const query = new URLSearchParams({ versionId: citation.versionId ?? '' });
    return `/api/organizations/${this.organizationId()}/documents/${citation.documentId}/download?${query}`;
  }
  reset(): void { this.conversationId.set(null); this.turns.set([]); this.question.set(''); this.error.set(''); this.notice.set(''); this.confirmingDelete.set(false); }
  requestDelete(): void { if (this.conversationId() && !this.pending()) this.confirmingDelete.set(true); }
  cancelDelete(): void { this.confirmingDelete.set(false); }
  deleteChat(): void {
    const org = this.organizationId(), id = this.conversationId();
    if (!org || !id || !this.confirmingDelete() || this.pending()) return;
    this.pending.set(true); this.error.set('');
    this.api.delete<void>(`/api/organizations/${org}/assistant/conversations/${id}`)
      .pipe(finalize(() => this.pending.set(false))).subscribe({
        next: () => { this.reset(); this.loadChats(); this.notice.set(this.preferences.text('Chat eliminado.')); },
        error: error => { this.confirmingDelete.set(false); this.error.set(accessError(error)); },
      });
  }
  loadChats(selectLatest = false): void {
    const org = this.organizationId(); if (!org) { this.chats.set([]); return; }
    this.api.get<ChatSummary[]>(`/api/organizations/${org}/assistant/conversations`).subscribe({
      next: chats => {
        if (this.organizationId() !== org) return;
        this.chats.set(chats);
        if (selectLatest && chats.length) this.openChat(chats[0].id);
      },
      error: error => this.error.set(accessError(error)),
    });
  }
  openChat(id: string): void {
    if (this.pending()) return;
    if (!id) { this.reset(); return; }
    const org = this.organizationId();
    this.api.get<Turn[]>(`/api/organizations/${org}/assistant/conversations/${id}`).subscribe({
      next: turns => { if (this.organizationId() !== org) return; this.conversationId.set(id); this.turns.set(turns); this.question.set(''); this.error.set(''); this.notice.set(''); },
      error: error => this.error.set(accessError(error)),
    });
  }
  ask(event: Event): void {
    event.preventDefault();
    const org = this.organizationId(), question = this.question().trim(); if (!org || !question || this.pending()) return;
    this.turns.update(turns => [...turns, { role: 'USER', content: question }]);
    this.question.set(''); this.pending.set(true); this.error.set(''); this.notice.set('');
    this.prepareForQuestion(org, question, 0);
  }
  private prepareForQuestion(org: string, question: string, batch: number): void {
    this.notice.set(this.preferences.text('Preparando documentación para la búsqueda…'));
    this.api.post<{ indexed: number; failed: number; morePending: boolean }>(`/api/organizations/${org}/assistant/index`, {}).subscribe({
      next: result => {
        if (result.morePending && result.indexed > 0 && batch < 99) { this.prepareForQuestion(org, question, batch + 1); return; }
        this.notice.set(result.morePending ? this.preferences.text('Algunos documentos siguen pendientes de preparación; puedes volver a preguntar para incluirlos.') : '');
        this.sendQuestion(org, question);
      },
      error: error => {
        if (error.status === 403) { this.notice.set(''); this.sendQuestion(org, question); return; }
        this.turns.update(turns => turns.slice(0, -1)); this.question.set(question); this.pending.set(false); this.notice.set(''); this.error.set(accessError(error));
      },
    });
  }
  private sendQuestion(org: string, question: string): void {
    this.api.post<Answer>(`/api/organizations/${org}/assistant/ask`, { conversationId: this.conversationId(), question, repositoryId: this.repositoryId() || null })
      .pipe(finalize(() => this.pending.set(false))).subscribe({
        next: result => {
          this.conversationId.set(result.conversationId);
          this.loadChats();
          const answer = result.status === 'NO_EVIDENCE' && this.preferences.language() === 'en'
            ? 'I could not find enough information in the sources you can access. Add more context or ask about another repository or package.'
            : result.answer;
          this.turns.update(turns => [...turns, { role: 'ASSISTANT', content: answer, citations: result.citations, proposedRepairPrompt: result.proposedRepairPrompt }]);
        },
        error: error => { this.turns.update(turns => turns.slice(0, -1)); this.question.set(question); this.error.set(accessError(error)); this.loadChats(true); },
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
