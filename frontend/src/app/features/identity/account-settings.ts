import { Component, effect, inject, OnInit, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { finalize } from 'rxjs';
import { SessionApiService, Account, accessError, WorkspaceLanguage, WorkspaceTheme } from '../../core/session-api.service';
import { PreferencesService } from '../../core/preferences.service';
import { WorkspaceSettingsService } from '../../core/workspace-settings.service';
import { PersonalNameDirective } from '../../core/personal-name.directive';
import { TranslatePipe } from '../../core/translate.pipe';

@Component({
  selector: 'app-account-settings',
  imports: [FormsModule, MatButtonModule, MatInputModule, MatFormFieldModule, PersonalNameDirective, TranslatePipe],
  template: `
    <section class="settings-card">
      <h2>{{ 'Perfil personal' | t }}</h2>
      <p>{{ 'Tu correo de acceso no se modifica desde esta pantalla.' | t }}</p>
      <form #profile="ngForm" (ngSubmit)="save(profile)">
        <mat-form-field appearance="outline">
          <mat-label>{{ 'Nombre completo' | t }}</mat-label>
          <input matInput appPersonalName name="displayName" [(ngModel)]="name" required maxlength="200" autocomplete="name" [disabled]="pending()">
          <mat-error>{{ 'Usa solo letras, espacios, apóstrofos y guiones.' | t }}</mat-error>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>{{ 'Correo electrónico' | t }}</mat-label>
          <input matInput [value]="api.account()?.email" readonly>
        </mat-form-field>
        <button mat-flat-button type="submit" [disabled]="pending() || profile.invalid">{{ 'Guardar perfil' | t }}</button>
      </form>
    </section>

    <section class="settings-card">
      <h2>{{ 'Configuración del espacio de trabajo' | t }}</h2>
      <p>{{ 'El tema y el idioma se comparten con las personas de la organización activa.' | t }}</p>
      @if (workspace.organizations().length) {
        <label class="organization-select">{{ 'Organización' | t }}
          <select [value]="workspace.activeOrganizationId()" (change)="chooseOrganization($event)">
            @for (organization of workspace.organizations(); track organization.id) {
              <option [value]="organization.id">{{ organization.name }}</option>
            }
          </select>
        </label>
        @if (workspace.loading() && !workspace.settings()) {
          <p role="status">Cargando configuración…</p>
        } @else if (workspace.settings(); as settings) {
          <div class="preferences">
            <label>{{ 'Tema del espacio' | t }}
              <select [value]="settings.theme" (change)="workspaceTheme($event)" [disabled]="pending() || !settings.canManage">
                <option value="SYSTEM">{{ 'Según el dispositivo' | t }}</option>
                <option value="LIGHT">{{ 'Claro' | t }}</option>
                <option value="DARK">{{ 'Oscuro' | t }}</option>
              </select>
            </label>
            <label>{{ 'Idioma del espacio' | t }}
              <select [value]="settings.language" (change)="workspaceLanguage($event)" [disabled]="pending() || !settings.canManage">
                <option value="ES">{{ 'Español' | t }}</option>
                <option value="EN">{{ 'English' | t }}</option>
              </select>
            </label>
          </div>
          @if (settings.canManage) {
            <button mat-flat-button type="button" (click)="saveWorkspace()" [disabled]="pending()">{{ 'Guardar configuración del espacio' | t }}</button>
          } @else {
            <p>{{ 'Solo propietarios y administradores pueden cambiar estos valores.' | t }}</p>
          }
        }
      } @else {
        <p>{{ 'Crea o acepta una invitación a una organización para configurar su espacio compartido.' | t }}</p>
      }
    </section>

    <section class="settings-card">
      <h2>{{ 'Apariencia e idioma personales' | t }}</h2>
      <p>{{ 'Se usan en acceso y registro. Dentro de una organización activa prevalece la configuración compartida de ese espacio.' | t }}</p>
      <div class="preferences">
        <label>{{ 'Apariencia personal' | t }}
          <select [value]="preferences.personalThemeValue()" (change)="theme($event)">
            <option value="system">{{ 'Según el dispositivo' | t }}</option><option value="light">{{ 'Claro' | t }}</option><option value="dark">{{ 'Oscuro' | t }}</option>
          </select>
        </label>
        <label>{{ 'Idioma personal' | t }}
          <select [value]="preferences.personalLanguageValue()" (change)="language($event)">
            <option value="es">{{ 'Español' | t }}</option><option value="en">{{ 'English' | t }}</option>
          </select>
        </label>
      </div>
    </section>

    <section class="settings-card">
      <h2>{{ 'Seguridad de la cuenta' | t }}</h2>
      <p>{{ 'Para cambiar tu contraseña, solicita un enlace privado en tu correo.' | t }}</p>
      <button mat-stroked-button (click)="resetPassword()" [disabled]="pending()">{{ 'Solicitar cambio de contraseña' | t }}</button>
    </section>
    @if (message()) { <p role="status">{{ message() | t }}</p> }
    @if (error()) { <p role="alert">{{ error() | t }}</p> }
  `,
  styles: `
    .settings-card{margin-top:22px;padding:24px;background:var(--di-surface);border:1px solid var(--di-border);border-radius:10px}
    h2{font-size:18px;font-weight:600;margin:0 0 10px}p{font-size:13px;color:var(--di-text-secondary);line-height:1.7}
    form{display:grid;gap:14px;margin-top:20px}form button{justify-self:start}
    .preferences{display:grid;gap:18px;margin:20px 0}label{display:grid;gap:8px;font-size:13px;color:var(--di-text)}
    select{width:100%;background:var(--di-surface);border:1px solid var(--di-control-border);color:var(--di-text);padding:10px;border-radius:6px}
    .organization-select{max-width:620px;margin-top:18px}.settings-card>button{margin-top:4px}
    [role=status]{color:var(--di-success-text)}[role=alert]{color:var(--di-critical-text)}
    @media(min-width:800px){.preferences{grid-template-columns:1fr 1fr}form{max-width:600px}}
  `,
})
export class AccountSettings implements OnInit {
  readonly api = inject(SessionApiService);
  readonly preferences = inject(PreferencesService);
  readonly workspace = inject(WorkspaceSettingsService);
  readonly pending = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  name = this.api.account()?.displayName ?? '';
  private nextTheme: WorkspaceTheme = 'SYSTEM';
  private nextLanguage: WorkspaceLanguage = 'ES';

  constructor() {
    effect(() => {
      const settings = this.workspace.settings();
      if (settings) { this.nextTheme = settings.theme; this.nextLanguage = settings.language; }
    });
  }

  ngOnInit(): void { this.workspace.initializeDefault(); }

  save(form: NgForm): void {
    if (this.pending() || form.invalid) return;
    this.pending.set(true); this.error.set(''); this.message.set('');
    this.api.post<Account>('/api/account/profile', { displayName: this.name }).pipe(finalize(() => this.pending.set(false)))
      .subscribe({ next: account => { this.api.account.set(account); this.message.set('Perfil actualizado.'); }, error: error => this.error.set(accessError(error)) });
  }

  chooseOrganization(event: Event): void {
    const id = (event.target as HTMLSelectElement).value;
    if (id) this.workspace.activate(id);
  }
  workspaceTheme(event: Event): void { this.nextTheme = (event.target as HTMLSelectElement).value as WorkspaceTheme; }
  workspaceLanguage(event: Event): void { this.nextLanguage = (event.target as HTMLSelectElement).value as WorkspaceLanguage; }
  saveWorkspace(): void {
    const current = this.workspace.settings();
    if (this.pending() || !current?.canManage) return;
    this.pending.set(true); this.error.set(''); this.message.set('');
    this.workspace.save(this.nextTheme, this.nextLanguage).pipe(finalize(() => this.pending.set(false)))
      .subscribe({ next: () => this.message.set('Configuración del espacio actualizada.'), error: error => this.error.set(accessError(error)) });
  }
  theme(event: Event): void { this.preferences.setTheme((event.target as HTMLSelectElement).value); }
  language(event: Event): void { this.preferences.setLanguage((event.target as HTMLSelectElement).value); }
  resetPassword(): void {
    if (this.pending()) return;
    this.pending.set(true); this.error.set(''); this.message.set('');
    this.api.post('/api/auth/password-reset/request', { email: this.api.account()?.email }).pipe(finalize(() => this.pending.set(false)))
      .subscribe({ next: () => this.message.set('Si tu cuenta admite contraseña local, recibirás un enlace privado para cambiarla.'), error: error => this.error.set(accessError(error)) });
  }
}
