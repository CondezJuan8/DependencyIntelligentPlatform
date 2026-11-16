import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { SessionApiService, accessError } from './core/session-api.service';
import { AccessForm } from './features/identity/access-form';
import { AccountLinkService } from './features/identity/account-link.service';
import { AccountAction } from './features/identity/account-action';
import { ExternalAuthService } from './features/identity/external-auth.service';
import { InvitationAcceptance } from './features/organizations/invitation-acceptance';
import { InvitationLinkService } from './features/organizations/invitation-link.service';
import { AuthFrame } from './features/identity/auth-frame';
import { ApplicationShell } from './shared/application-shell';

@Component({
  imports: [
    MatButtonModule,
    AccessForm,
    AccountAction,
    InvitationAcceptance,
    AuthFrame,
    ApplicationShell,
  ],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App implements OnInit {
  readonly session = inject(SessionApiService);
  readonly links = inject(AccountLinkService);
  readonly external = inject(ExternalAuthService);
  readonly invitationLinks = inject(InvitationLinkService);
  readonly loading = signal(true);
  readonly error = signal('');

  ngOnInit(): void {
    this.restore();
  }

  restore(): void {
    this.loading.set(true);
    this.error.set('');
    this.session
      .restore()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        error: (error) => this.error.set(accessError(error)),
      });
  }
}
