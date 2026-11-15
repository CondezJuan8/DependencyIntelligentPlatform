import { Component, inject, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { accessError, SessionApiService } from '../../core/session-api.service';

@Component({
  selector: 'app-access-form',
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './access-form.html',
  styleUrl: './access-form.scss',
})
export class AccessForm {
  private readonly api = inject(SessionApiService);
  readonly registering = signal(false);
  readonly pending = signal(false);
  readonly error = signal('');
  email = '';
  displayName = '';
  password = '';
  confirmation = '';

  changeMode(): void {
    this.registering.update(value => !value);
    this.error.set('');
    this.password = '';
    this.confirmation = '';
  }

  submit(form: NgForm): void {
    if (this.pending() || form.invalid) return;
    if (this.registering() && this.password !== this.confirmation) {
      this.error.set('Las contraseñas deben coincidir.');
      return;
    }
    this.pending.set(true);
    this.error.set('');
    this.api.authenticate(this.registering() ? 'register' : 'login', {
      email: this.email, password: this.password, displayName: this.displayName,
    }).pipe(finalize(() => this.pending.set(false))).subscribe({
      next: () => { this.password = ''; this.confirmation = ''; },
      error: error => this.error.set(accessError(error)),
    });
  }
}
