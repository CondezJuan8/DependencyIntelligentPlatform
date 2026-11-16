import { PreferenceControls } from './preference-controls';
import { Component, effect, inject, input, output, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { navigation, availableNavigation, Page } from '../core/navigation';
import { SessionApiService, accessError } from '../core/session-api.service';
import { DiIcon } from './di-icon';
@Component({
  selector: 'app-workspace-topbar',
  imports: [PreferenceControls,DiIcon],
  templateUrl: './workspace-topbar.html',
  styleUrl: './workspace-topbar.scss',
})
export class WorkspaceTopbar {
  readonly api = inject(SessionApiService);
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
    return availableNavigation(this.api.account()).filter((item) =>
      item.label.toLocaleLowerCase().includes(this.query().trim().toLocaleLowerCase()),
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
