import { PreferenceControls } from './preference-controls';
import { Component, effect, inject, input, output, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { navigation, availableNavigation, Page } from '../core/navigation';
import { SessionApiService, accessError } from '../core/session-api.service';
import { PreferencesService } from '../core/preferences.service';
import { TranslatePipe } from '../core/translate.pipe';
import { DiIcon } from './di-icon';
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
  readonly query = signal('');
  readonly profile = signal(false);
  readonly notices = signal(false);
  readonly pending = signal(false);
  readonly error = signal('');
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
