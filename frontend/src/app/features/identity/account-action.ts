import { Component, inject, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { accessError, SessionApiService } from '../../core/session-api.service';
import { AccountLinkService } from './account-link.service';

@Component({
  selector: 'app-account-action',
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './account-action.html',
  styleUrl: './access-form.scss',
})
export class AccountAction {
  readonly links = inject(AccountLinkService);
  private readonly api = inject(SessionApiService);
  readonly action = this.links.action()!;
  readonly verification = this.action.kind === 'verify-email';
  readonly valid = /^[A-Za-z0-9_-]{43}$/.test(this.action.token);
  readonly pending = signal(false);
  readonly done = signal(false);
  readonly error = signal(this.valid ? '' : 'El enlace no es válido. Solicita uno nuevo.');
  password = '';
  confirmation = '';

  submit(form: NgForm): void {
    if (!this.valid || this.pending() || this.done() || form.invalid) return;
    if (!this.verification && this.password !== this.confirmation) {
      this.error.set('Las contraseñas deben coincidir.');
      return;
    }
    this.pending.set(true);
    this.error.set('');
    const body = this.verification ? { token: this.action.token } : { token: this.action.token, password: this.password };
    const path = this.verification ? '/api/auth/email-verification/confirm' : '/api/auth/password-reset/confirm';
    this.api.post<void>(path, body).pipe(finalize(() => this.pending.set(false))).subscribe({
      next: () => {
        this.password = '';
        this.confirmation = '';
        this.action.token = '';
        this.done.set(true);
        if (!this.verification) this.api.account.set(null);
      },
      error: error => this.error.set(accessError(error)),
    });
  }

  continue(): void {
    this.action.token = '';
    this.api.restore().subscribe({
      next: () => this.links.action.set(null),
      error: error => this.error.set(accessError(error)),
    });
  }
}
