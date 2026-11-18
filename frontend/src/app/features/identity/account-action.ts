import { Component, inject, OnInit, signal } from '@angular/core';
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
export class AccountAction implements OnInit {
  readonly links = inject(AccountLinkService);
  private readonly api = inject(SessionApiService);
  readonly action = this.links.action()!;
  readonly verification = this.action.kind === 'verify-email';
  readonly admissionDecision = this.action.kind === 'admission-decision';
  readonly valid = /^[A-Za-z0-9_-]{43}$/.test(this.action.token);
  readonly pending = signal(false);
  readonly refreshing = signal(false);
  readonly done = signal(false);
  readonly decisionMessage = signal('');
  readonly error = signal(this.valid ? '' : 'El enlace no es válido. Solicita uno nuevo.');
  password = '';
  confirmation = '';

  ngOnInit(): void {
    if (this.admissionDecision) this.applyAdmissionDecision();
  }

  private applyAdmissionDecision(): void {
    if (!this.valid || this.pending()) return;
    this.pending.set(true);
    this.error.set('');
    this.api.post<{ decision: 'APPROVE' | 'REJECT' }>('/api/platform/admission-decisions', { token: this.action.token })
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({
        next: result => {
          this.action.token = '';
          this.decisionMessage.set(result.decision === 'APPROVE'
            ? 'Cuenta aprobada. La persona ya puede iniciar sesión.'
            : 'Solicitud rechazada. No se habilitó el acceso.');
          this.done.set(true);
        },
        error: error => this.error.set(accessError(error)),
      });
  }

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
        if (this.verification) this.refreshAfterVerification();
        else this.api.account.set(null);
      },
      error: error => this.error.set(accessError(error)),
    });
  }

  continue(): void {
    if (this.done() && this.verification) {
      this.refreshAfterVerification();
      return;
    }
    this.links.action.set(null);
  }

  private refreshAfterVerification(): void {
    if (this.refreshing()) return;
    this.refreshing.set(true);
    this.api.restore().pipe(finalize(() => this.refreshing.set(false))).subscribe({
      next: account => {
        this.links.notice.set(account?.emailVerified
          ? account.admissionStatus === 'PENDING_REVIEW'
            ? 'Correo verificado. Tu cuenta sigue pendiente de aprobación del administrador.'
            : 'Correo verificado correctamente. El estado de tu cuenta se actualizó.'
          : 'Tu correo ya está verificado. Inicia sesión para consultar el estado de tu cuenta.');
        this.links.action.set(null);
      },
      error: () => {
        this.links.notice.set('Correo verificado. Inicia sesión para consultar el estado actualizado.');
        this.links.action.set(null);
      },
    });
  }
}
