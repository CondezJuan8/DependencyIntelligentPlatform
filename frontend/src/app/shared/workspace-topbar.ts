import { PreferenceControls } from './preference-controls';
import { Component, computed, effect, inject, input, output, signal, viewChild, ElementRef } from '@angular/core';
import { finalize, forkJoin, from, of } from 'rxjs';
import { catchError, map, mergeMap, toArray } from 'rxjs/operators';
import { navigation, availableNavigation, Page } from '../core/navigation';
import { SessionApiService, accessError } from '../core/session-api.service';
import { WorkspaceSettingsService } from '../core/workspace-settings.service';
import { PreferencesService } from '../core/preferences.service';
import { TranslatePipe } from '../core/translate.pipe';
import { DiIcon } from './di-icon';
import { fixesForPackage } from '../features/inventory/inventory-evidence';
interface AlertRepository { id:string; organizationId:string; organizationName:string; fullName:string; }
interface AlertAdvisory { id:string; summary:string|null; severity:string; url:string; modifiedAt:string|null; affectedJson:string; }
interface AlertAssessment { packageVersion:{ecosystem:string;namespace:string|null;name:string;version:string}; status:string; checkedAt:string|null; advisories:AlertAdvisory[]; }
interface AlertPayload { assessments:AlertAssessment[]; }
interface WorkspaceAlert { key:string; title:string; summary:string; repository:string; href:'vulnerabilities'|'inventory'; severity:string; }
@Component({
  selector: 'app-workspace-topbar',
  imports: [PreferenceControls,DiIcon,TranslatePipe],
  templateUrl: './workspace-topbar.html',
  host: { '(document:keydown.control.k)': 'searchShortcut($event)', '(document:keydown.meta.k)': 'searchShortcut($event)' },
  styleUrl: './workspace-topbar.scss',
})
export class WorkspaceTopbar {
  readonly api = inject(SessionApiService);
  readonly workspace = inject(WorkspaceSettingsService);
  private readonly preferences = inject(PreferencesService);
  readonly page = input.required<Page>();
  readonly menuOpen = input(false);
  readonly navigate = output<Page>();
  readonly toggleMenu = output<void>();
  readonly openAssistant = output<void>();
  readonly query = signal('');
  readonly activeResult = signal(0);
  private readonly searchDialog = viewChild.required<ElementRef<HTMLDialogElement>>('searchDialog');
  readonly matches = computed(() => {
    const query = this.normalize(this.query().trim());
    return availableNavigation(this.api.account()).filter(item =>
      this.normalize(this.preferences.text(item.label)).includes(query) ||
      this.normalize(item.label).includes(query),
    );
  });
  readonly profile = signal(false);
  readonly notices = signal(false);
  readonly pending = signal(false);
  readonly error = signal('');
  readonly notificationItems = signal<WorkspaceAlert[]>([]);
  private readonly loadedNotificationKeys = signal<string[]>([]);
  readonly notificationsLoading = signal(false);
  readonly notificationsError = signal('');
  readonly notificationsPartial = signal(false);
  private dismissedAlerts = new Set<string>();
  private dismissedAccountId = '';
  constructor() {
    effect(() => {
      this.page();
      this.reset();
    });
  }
  get initials(): string {
    return (
      this.api
        .account()
        ?.displayName.trim()
        .split(/\s+/)
        .slice(0, 2)
        .map((part) => part[0])
        .join('') ?? 'DI'
    ).toUpperCase();
  }
  private normalize(value: string): string {
    return value.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLocaleLowerCase();
  }
  searchShortcut(event: Event): void {
    if (this.menuOpen() || document.querySelector('[aria-modal="true"]')) return;
    event.preventDefault();
    this.openSearch(this.searchDialog().nativeElement);
  }
  openSearch(dialog: HTMLDialogElement): void {
    if (dialog.open) return;
    this.reset();
    dialog.showModal();
  }
  dismissSearchBackdrop(event: MouseEvent, dialog: HTMLDialogElement): void {
    if (event.target === dialog) this.closeSearch(dialog);
  }
  cancelSearch(event: Event, dialog: HTMLDialogElement): void {
    event.preventDefault();
    event.stopPropagation();
    this.closeSearch(dialog);
  }
  closeSearch(dialog: HTMLDialogElement): void {
    dialog.close();
    document.getElementById('workspace-search-trigger')?.focus();
  }
  searchKey(event: KeyboardEvent, dialog: HTMLDialogElement): void {
    if (event.isComposing) return;
    const results = this.matches();
    if (!results.length) return;
    if (event.key === 'Enter') {
      event.preventDefault();
      this.chooseSection(results[this.activeResult()].id, dialog);
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const offset = event.key === 'ArrowDown' ? 1 : -1;
      const index = (this.activeResult() + offset + results.length) % results.length;
      this.activeResult.set(index);
      document.getElementById('workspace-section-' + results[index].id)?.scrollIntoView({ block: 'nearest' });
    }
  }
  chooseSection(page: Page, dialog: HTMLDialogElement): void {
    dialog.close();
    this.go(page);
  }
  closePopovers(): void {
    const trigger = this.profile() ? 'workspace-profile-trigger' : this.notices() ? 'workspace-notifications-trigger' : null;
    if (trigger) document.getElementById(trigger)?.focus();
    this.reset();
  }
  get currentPage() {
    return navigation.find((item) => item.id === this.page()) ?? navigation[0];
  }
  search(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
    this.activeResult.set(0);
  }
  reset(): void {
    this.query.set('');
    this.activeResult.set(0);
    this.profile.set(false);
    this.notices.set(false);
  }
  go(page: Page): void {
    this.reset();
    this.navigate.emit(page);
  }
  toggleNotifications(): void {
    const opening=!this.notices();
    this.notices.set(opening); this.profile.set(false);
    if(opening) this.loadNotifications();
  }
  private dismissalStorageKey(): string | null {
    const accountId=this.api.account()?.id;
    return accountId ? `di.notifications.dismissed.${accountId}` : null;
  }
  private restoreDismissedAlerts(): void {
    const accountId=this.api.account()?.id ?? '';
    if(accountId===this.dismissedAccountId) return;
    this.dismissedAccountId=accountId;
    this.dismissedAlerts=new Set<string>();
    const storageKey=this.dismissalStorageKey();
    if(!storageKey) return;
    try {
      const stored=JSON.parse(localStorage.getItem(storageKey) ?? '[]');
      if(Array.isArray(stored)) this.dismissedAlerts=new Set(stored.filter((key):key is string=>typeof key==='string'));
    } catch { /* Storage may be unavailable; dismissal still works for this session. */ }
  }
  private saveDismissedAlerts(): void {
    const storageKey=this.dismissalStorageKey();
    if(!storageKey) return;
    try { localStorage.setItem(storageKey,JSON.stringify([...this.dismissedAlerts])); } catch {}
  }
  dismissNotification(key:string): void {
    this.restoreDismissedAlerts();
    this.dismissedAlerts.add(key);
    this.notificationItems.update(items=>items.filter(item=>item.key!==key));
    this.saveDismissedAlerts();
  }
  dismissAllNotifications(): void {
    this.restoreDismissedAlerts();
    for(const key of this.loadedNotificationKeys()) this.dismissedAlerts.add(key);
    this.notificationItems.set([]);
    this.saveDismissedAlerts();
  }
  loadNotifications(): void {
    if(this.notificationsLoading()) return;
    this.restoreDismissedAlerts();
    this.notificationsLoading.set(true); this.notificationsError.set('');
    this.notificationsPartial.set(false);
    this.api.get<AlertRepository[]>('/api/account/workspace').pipe(
      mergeMap(repositories => repositories.length ? from(repositories).pipe(mergeMap(repository => {
        const path=`/api/organizations/${repository.organizationId}/repositories/${repository.id}/vulnerability-analysis`;
        return this.api.get<AlertPayload>(path).pipe(catchError(()=>of(null)),map(result=>({repository,result})));
      },4),toArray()) : of([])),
      finalize(()=>this.notificationsLoading.set(false)),
    ).subscribe({next:results=>{
      const alerts=new Map<string,WorkspaceAlert>();
      this.notificationsPartial.set(results.some(({result})=>!result));
      for(const {repository,result} of results){
        if(!result) continue;
        for(const assessment of result.assessments??[]){
          const {ecosystem,namespace,name,version}=assessment.packageVersion;
          const packageName=ecosystem==='MAVEN'&&namespace?`${namespace}:${name}`:namespace?`${namespace}/${name}`:name;
          for(const advisory of assessment.advisories??[]){
            const modifiedAt=advisory.modifiedAt?Date.parse(advisory.modifiedAt):Number.NaN;
            const updated=Number.isFinite(modifiedAt)&&modifiedAt>Date.now()-7*24*60*60*1000;
            const key=`${repository.id}|${packageName}|${version}|${advisory.id}|${advisory.modifiedAt??''}`;
            const packageCoordinate=ecosystem==='MAVEN'&&namespace?`${namespace}:${name}`:name;
            const fixed=fixesForPackage(advisory.affectedJson,ecosystem,packageCoordinate);
            const fixSummary=fixed.length?` · ${this.preferences.text('Versión corregida:')} ${fixed.join(', ')}`:'';
            alerts.set(key,{key,title:updated?'Actualización reciente en OSV':'Versión vulnerable',summary:`${packageName}@${version} · ${advisory.id}${advisory.summary?` · ${advisory.summary}`:''}${fixSummary}`,repository:repository.fullName,href:'vulnerabilities',severity:advisory.severity||'UNKNOWN'});
          }
          if(['STALE','INCOMPLETE','UNAVAILABLE','NOT_ASSESSED'].includes(assessment.status)) {
            const key=`${repository.id}|${packageName}|${version}|${assessment.status}`;
            alerts.set(key,{key,title:'Evaluación OSV pendiente',summary:`${packageName}@${version} · ${assessment.status}`,repository:repository.fullName,href:'inventory',severity:'REVIEW'});
          }
        }
      }
      this.loadedNotificationKeys.set([...alerts.keys()]);
      this.notificationItems.set([...alerts.values()].filter(item=>!this.dismissedAlerts.has(item.key)).slice(0,30));
    },error:error=>this.notificationsError.set(accessError(error))});
  }
  logout(): void {
    if (this.pending()) return;
    this.pending.set(true);
    this.error.set('');
    this.api
      .logout()
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({ error: (error) => this.error.set(accessError(error)) });
  }
}
