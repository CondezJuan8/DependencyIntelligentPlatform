import { Component } from '@angular/core';
import { PreferenceControls } from '../../shared/preference-controls';

@Component({
  selector: 'app-auth-frame',
  imports: [PreferenceControls],
  template: `<div class="auth-layout">
    <aside class="brand-panel" aria-label="Dependency Intelligence">
      <a class="brand" href="/">
        <img src="/brand-mark.svg" alt="" width="42" height="48" />
        <span>Dependency<br />Intelligence</span>
      </a>
      <div class="brand-copy">
        <h1>Comprende.<br />Anticipa.<br />Protege.</h1>
        <p>Una nueva perspectiva sobre tus dependencias, sus riesgos y el impacto de cada cambio.</p>
      </div>
      <img class="brand-art" src="/auth-network.svg" alt="" aria-hidden="true" width="760" height="460" />
    </aside>
    <main class="form-panel">
      <div class="auth-tools">
        <a class="mobile-brand" href="/">
          <img src="/brand-mark.svg" alt="" width="28" height="32" />
          <span>Dependency<br />Intelligence</span>
        </a>
        <app-preference-controls />
      </div>
      <div class="form-content"><ng-content /></div>
    </main>
  </div>`,
  styles: `
    :host{display:block;min-height:100svh;background:var(--di-background);color:var(--di-text)}
    .auth-layout{min-height:100svh;display:flex;flex-direction:column;background:var(--di-background)}
    .brand-panel{display:none}
    .form-panel{min-height:100svh;display:flex;flex-direction:column;gap:22px;padding:14px 16px 24px;background:var(--di-background)}
    .auth-tools{display:flex;align-items:center;justify-content:space-between;gap:12px;width:100%;max-width:480px;margin:0 auto}
    .mobile-brand{display:flex;align-items:center;gap:8px;color:var(--di-text);font-size:12px;font-weight:650;line-height:1.08;text-decoration:none}
    .mobile-brand img{flex:none}
    .form-content{width:100%;max-width:460px;flex:none;margin:0 auto;padding:22px;border:1px solid var(--di-border);border-radius:var(--di-radius-dialog);background:var(--di-surface);box-shadow:0 18px 48px #07130a12}
    .form-content>*{width:100%}
    @media(min-width:600px){.form-panel{padding:20px 32px 32px}.form-content{padding:30px;margin-block:auto}.auth-tools{max-width:460px}}
    @media(min-width:960px){
      .auth-layout{display:grid;grid-template-columns:minmax(390px,44%) minmax(0,1fr)}
      .brand-panel{position:relative;isolation:isolate;display:flex;flex-direction:column;min-height:100svh;overflow:hidden;padding:clamp(34px,4vw,64px);background:var(--di-navy);color:#f1f6e9}
      .brand-panel::before{position:absolute;z-index:-1;inset:0;content:"";background:url('/atlas-contours.svg') right bottom / min(100%,800px) auto no-repeat;opacity:.9}
      .brand{display:flex;gap:13px;align-items:center;width:max-content;color:#fff;text-decoration:none;font-size:18px;font-weight:650;line-height:1.12}
      .brand img{filter:drop-shadow(0 4px 12px #07130a66)}
      .brand-copy{position:relative;z-index:1;max-width:400px;margin:auto 0 15vh}
      .brand-copy h1{margin:0 0 20px;font-size:clamp(42px,4.4vw,60px);line-height:1.02;letter-spacing:-.055em;font-weight:600}
      .brand-copy p{max-width:38ch;margin:0;color:#d0ddc9;font-size:15px;line-height:1.7}
      .brand-art{position:absolute;z-index:0;right:-3%;bottom:-2%;width:min(100%,760px);height:auto;opacity:.94;pointer-events:none}
      .form-panel{min-height:100svh;gap:12px;padding:22px clamp(28px,5vw,72px);background:var(--di-background)}
      .mobile-brand{display:none}
      .auth-tools{justify-content:flex-end;max-width:none}
      .form-content{flex:0 1 auto;display:flex;flex-direction:column;justify-content:center;margin:auto;padding:34px;width:min(100%,460px)}
    }
    @media(max-height:700px) and (min-width:960px){.brand-copy{margin-bottom:8vh}.brand-copy h1{font-size:clamp(38px,3.7vw,50px);margin-bottom:14px}.brand-art{width:min(88%,640px)}.form-panel{padding-block:14px}.form-content{padding:26px}}
    @media(max-height:620px){.form-panel{min-height:100svh;gap:10px}.form-content{padding:18px}}
    @media(max-width:480px){.form-panel{padding-inline:14px}.form-content{padding:19px;border-radius:12px}}
    @media(prefers-reduced-motion:no-preference){.brand-copy{animation:auth-arrive 650ms cubic-bezier(.22,1,.36,1) both}.brand-art{animation:map-arrive 900ms cubic-bezier(.22,1,.36,1) 90ms both}.form-content{animation:auth-arrive 480ms cubic-bezier(.22,1,.36,1) both}}
    @keyframes auth-arrive{from{opacity:0;transform:translateY(12px)}to{opacity:1;transform:translateY(0)}}
    @keyframes map-arrive{from{opacity:0;transform:translate3d(10px,14px,0) scale(.985)}to{opacity:.94;transform:translate3d(0,0,0) scale(1)}}
    @media(prefers-reduced-motion:reduce){.brand-copy,.brand-art,.form-content{animation:none}}
  `,
})
export class AuthFrame {}
