import { PreferenceControls } from './preference-controls';
import { Component, effect, inject, input, output, signal } from '@angular/core';
import { finalize, forkJoin, from, of } from 'rxjs';
import { catchError, map, mergeMap, toArray } from 'rxjs/operators';
import { navigation, availableNavigation, Page } from '../core/navigation';
import { SessionApiService, accessError } from '../core/session-api.service';
import { PreferencesService } from '../core/preferences.service';
import { TranslatePipe } from '../core/translate.pipe';
import { DiIcon } from './di-icon';
import { fixesForPackage } from '../features/inventory/inventory-evidence';
interface AlertRepository { id:string; organizationId:string; organizationName:string; fullName:string; }
interface AlertAdvisory { id:string; summary:string|null; severity:string; url:string; modifiedAt:string|null; affectedJson:string; }
interface AlertAssessment { packageVersion:{ecosystem:string;namespace:string|null;name:string;version:string}; status:string; checkedAt:string|null; advisories:AlertAdvisory[]; }
interface AlertPayload { assessments:AlertAssessment[]; }
interface WorkspaceAlert { title:string; summary:string; repository:string; href:'vulnerabilities'|'inventory'; severity:string; }
@Component({
  selector: 'app-workspace-topbar',
  imports: [PreferenceControls,DiIcon,TranslatePipe],
  templateUrl: './workspace-topbar.html',
  styleUrl: './workspace-topbar.scss',
})
export class WorkspaceTopbar {
  readonly api = inject(SessionApiService);
  private readonly preferences = inject(PreferencesService);
  readonly page = input.required<Page>();
  readonly menuOpen = input(false);
  readonly navigate = output<Page>();
  readonly toggleMenu = output<void>();
  readonly openAssistant = output<void>();
  readonly query = signal('');
  readonly profile = signal(false);
  readonly notices = signal(false);
  readonly pending = signal(false);
  readonly error = signal('');
  readonly notificationItems = signal<WorkspaceAlert[]>([]);
  readonly notificationsLoading = signal(false);
  readonly notificationsError = signal('');
  readonly notificationsPartial = signal(false);
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
  get matches() {
    const query = this.query().trim().toLocaleLowerCase();
    return availableNavigation(this.api.account()).filter((item) =>
      this.preferences.text(item.label).toLocaleLowerCase().includes(query) ||
      item.label.toLocaleLowerCase().includes(query),
    );
  }
  get currentPage() {
    return navigation.find((item) => item.id === this.page()) ?? navigation[0];
  }
  search(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }
  reset(): void {
    this.query.set('');
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
  loadNotifications(): void {
    if(this.notificationsLoading()) return;
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
            const key=`${repository.id}|${packageName}|${version}|${advisory.id}`;
            const packageCoordinate=ecosystem==='MAVEN'&&namespace?`${namespace}:${name}`:name;
            const fixed=fixesForPackage(advisory.affectedJson,ecosystem,packageCoordinate);
            const fixSummary=fixed.length?` · ${this.preferences.text('Versión corregida:')} ${fixed.join(', ')}`:'';
            alerts.set(key,{title:updated?'Actualización reciente en OSV':'Versión vulnerable',summary:`${packageName}@${version} · ${advisory.id}${advisory.summary?` · ${advisory.summary}`:''}${fixSummary}`,repository:repository.fullName,href:'vulnerabilities',severity:advisory.severity||'UNKNOWN'});
          }
          if(['STALE','INCOMPLETE','UNAVAILABLE','NOT_ASSESSED'].includes(assessment.status)) alerts.set(`${repository.id}|${packageName}|${version}|${assessment.status}`,{title:'Evaluación OSV pendiente',summary:`${packageName}@${version} · ${assessment.status}`,repository:repository.fullName,href:'inventory',severity:'REVIEW'});
        }
      }
      this.notificationItems.set([...alerts.values()].slice(0,30));
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
