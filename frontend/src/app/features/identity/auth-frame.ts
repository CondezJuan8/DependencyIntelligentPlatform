import { Component } from '@angular/core';
import { TranslatePipe } from '../../core/translate.pipe';
import { PreferenceControls } from '../../shared/preference-controls';

@Component({
  selector: 'app-auth-frame',
  imports: [PreferenceControls, TranslatePipe],
  template: `<div class="auth-layout">
    <aside class="brand-panel" aria-label="Dependency Intelligence">
      <a class="brand" href="/">
        <img src="/brand-mark.svg" alt="" width="40" height="46" />
        <span>Dependency<br />Intelligence</span>
      </a>
      <div class="brand-copy">
        <span class="brand-kicker">{{ 'Observatorio de Evidencias' | t }}</span>
        <p class="brand-title">{{ 'Inteligencia de' | t }}<br /><em>{{ 'dependencias.' | t }}</em></p>
        <p class="brand-description">{{ 'Reúne el inventario, los avisos OSV y la documentación de tus repositorios autorizados.' | t }}</p>
      </div>
      <p class="brand-footnote">{{ 'Acceso controlado por organización' | t }}</p>
    </aside>
    <main class="form-panel">
      <div class="auth-tools">
        <a class="mobile-brand" href="/">
          <img src="/brand-mark.svg" alt="" width="28" height="32" />
          <span>Dependency Intelligence</span>
        </a>
        <app-preference-controls />
      </div>
      <div class="form-content"><ng-content /></div>
    </main>
  </div>`,
  styles: `
    :host { display: block; min-height: 100svh; background: var(--di-background); color: var(--di-text); }
    .auth-layout { display: grid; min-height: 100svh; grid-template-columns: minmax(0, 46fr) minmax(0, 54fr); }
    .brand-panel { position: relative; isolation: isolate; overflow: hidden; display: grid; grid-template-rows: auto 1fr auto; align-items: start; gap: 20px; padding: clamp(26px, 4vw, 58px); border-inline-end: 1px solid var(--di-border); background: var(--di-surface-muted); }
    .brand-panel::before {
      position: absolute;
      z-index: -1;
      top: 55%;
      right: -34%;
      width: 92%;
      aspect-ratio: 1;
      border-radius: 50%;
      background: repeating-radial-gradient(ellipse at center, transparent 0 12px, var(--di-illustration-line) 13px 14px, transparent 15px 19px);
      opacity: .23;
      mask-image: radial-gradient(ellipse, #000 52%, transparent 72%);
      content: '';
    }
    .brand, .mobile-brand { display: flex; align-items: center; gap: 10px; width: max-content; max-width: 100%; color: var(--di-text); text-decoration: none; font-size: 14px; font-weight: 700; line-height: 1.2; }
    .brand img, .mobile-brand img { width: 34px; height: 36px; flex: none; padding: 5px; border-radius: var(--di-radius-control); background: var(--di-paper); }
    .brand-copy { align-self: center; max-width: 540px; }
    .brand-kicker { display: block; margin-bottom: 16px; color: var(--di-accent-text); font: 600 11px/1.5 var(--di-mono-family); letter-spacing: .06em; text-transform: uppercase; }
    .brand-kicker::before { display: inline-block; width: 18px; height: 1px; margin-inline-end: 8px; vertical-align: middle; background: currentColor; content: ''; }
    .brand-title { margin: 0; font-size: clamp(33px, 4.5vw, 64px); font-weight: 750; letter-spacing: -.047em; line-height: 1.03; }
    .brand-title em { color: var(--di-accent-text); font-style: normal; }
    .brand-description { max-width: 34ch; margin: 20px 0 0; color: var(--di-text-secondary); font-size: 15px; }
    .brand-footnote { margin: 0; color: var(--di-text-secondary); font-size: 11px; }
    .form-panel { display: flex; flex-direction: column; min-width: 0; gap: 28px; padding: 25px clamp(24px, 6.5vw, 100px) 32px; }
    .auth-tools { display: flex; justify-content: flex-end; }
    .mobile-brand { display: none; }
    .form-content { width: min(100%, 460px); margin: auto; }
    @media (max-width: 860px) {
      .auth-layout { grid-template-columns: minmax(0, 1fr); }
      .brand-panel { display: none; }
      .form-panel { gap: 36px; padding-inline: max(16px, 4vw); }
      .auth-tools { justify-content: space-between; gap: 6px; }
      .mobile-brand { display: flex; gap: 6px; font-size: 12px; }
      .mobile-brand img { width: 29px; height: 31px; }
      .form-content { margin: 0 auto; }
    }
    @media (prefers-reduced-motion: no-preference) {
      .brand-copy, .form-content { animation: arrive 350ms cubic-bezier(.2,.8,.2,1) both; }
    }
    @keyframes arrive { from { opacity: .65; transform: translateY(5px); } to { opacity: 1; transform: none; } }
  `,
})
export class AuthFrame {}
