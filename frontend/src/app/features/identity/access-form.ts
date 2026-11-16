import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { accessError, SessionApiService } from '../../core/session-api.service';
import { ExternalSignIn } from './external-sign-in';
import { DiIcon } from '../../shared/di-icon';

@Component({
  selector: 'app-access-form',
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    ExternalSignIn,
    DiIcon,
  ],
  templateUrl: './access-form.html',
  styleUrl: './access-form.scss',
})
export class AccessForm {
  private readonly api = inject(SessionApiService);
  readonly mode = signal<'login' | 'register' | 'recover'>('login');
  readonly registering = computed(() => this.mode() === 'register');
  readonly recovering = computed(() => this.mode() === 'recover');
  readonly pending = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  readonly showPassword = signal(false);
  email = '';
  displayName = '';
  password = '';
  confirmation = '';

  tabKey(event: KeyboardEvent): void {
    if (this.pending() || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const mode =
      event.key === 'Home'
        ? 'login'
        : event.key === 'End'
          ? 'register'
          : this.registering()
            ? 'login'
            : 'register';
    this.changeMode(mode);
    document.getElementById(mode === 'login' ? 'login-tab' : 'register-tab')?.focus();
  }

  changeMode(mode: 'login' | 'register' | 'recover'): void {
    this.mode.set(mode);
    this.error.set('');
    this.message.set('');
    this.password = '';
    this.confirmation = '';
    this.showPassword.set(false);
  }

  submit(form: NgForm): void {
    if (this.pending() || form.invalid) return;
    if (this.registering() && this.password !== this.confirmation) {
      this.error.set('Las contraseñas deben coincidir.');
      return;
    }
    this.pending.set(true);
    this.error.set('');
    if (this.recovering()) {
      this.api
        .post<void>('/api/auth/password-reset/request', { email: this.email })
        .pipe(finalize(() => this.pending.set(false)))
        .subscribe({
          next: () =>
            this.message.set(
              'Si la cuenta admite recuperación, recibirás un enlace para cambiar la contraseña.',
            ),
          error: (error) => this.error.set(accessError(error)),
        });
      return;
    }
    this.api
      .authenticate(this.registering() ? 'register' : 'login', {
        email: this.email,
        password: this.password,
        displayName: this.displayName,
      })
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({
        next: () => {
          this.password = '';
          this.confirmation = '';
        },
        error: (error) => this.error.set(accessError(error)),
      });
  }
}
