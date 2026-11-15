import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { SessionApiService, accessError } from './core/session-api.service';
import { AccessForm } from './features/identity/access-form';
import { OrganizationWorkspace } from './features/organizations/organization-workspace';

@Component({
  imports: [MatButtonModule, AccessForm, OrganizationWorkspace],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App implements OnInit {
  readonly session = inject(SessionApiService);
  readonly loading = signal(true);
  readonly pending = signal(false);
  readonly error = signal('');

  ngOnInit(): void { this.restore(); }

  restore(): void {
    this.loading.set(true);
    this.error.set('');
    this.session.restore().pipe(finalize(() => this.loading.set(false))).subscribe({
      error: error => this.error.set(accessError(error)),
    });
  }

  logout(): void {
    if (this.pending()) return;
    this.pending.set(true);
    this.error.set('');
    this.session.logout().pipe(finalize(() => this.pending.set(false))).subscribe({
      error: error => this.error.set(accessError(error)),
    });
  }
}
