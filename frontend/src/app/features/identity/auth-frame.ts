import { Component } from '@angular/core';
import { PreferenceControls } from '../../shared/preference-controls';

@Component({
  selector: 'app-auth-frame',
  imports: [PreferenceControls],
  template: `<div class="auth-layout">
    <aside class="brand-panel" aria-label="Dependency Intelligence">
      <a class="brand" href="/">
        <img src="/brand-mark.svg" alt="" width="40" height="46" />
        <span>Dependency<br />Intelligence</span>
      </a>
      <div class="brand-copy">
        <h1>Del origen<br />a la decisión.</h1>
        <p>Sigue la evidencia desde el repositorio hasta la versión y el aviso que puedes revisar.</p>
      </div>
      <div class="evidence-route" aria-label="Repositorio, análisis y avisos publicados">
        <span><i aria-hidden="true"></i>Repositorio</span>
        <span><i aria-hidden="true"></i>Análisis</span>
        <span><i aria-hidden="true"></i>Evidencia OSV</span>
      </div>
      <img class="brand-art" src="/auth-network.svg" alt="" aria-hidden="true" width="820" height="540" />
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
    :host{display:block;min-height:100svh;background:var(--di-background);color:var(--di-text)}
    .auth-layout{min-height:100svh;display:flex;flex-direction:column;background:var(--di-background)}
    .brand-panel{display:none}
    .form-panel{min-height:100svh;display:flex;flex-direction:column;gap:20px;padding:13px 14px 24px;background:var(--di-background)}
    .auth-tools{display:flex;align-items:center;justify-content:space-between;gap:10px;width:100%;max-width:470px;margin:0 auto}
    .mobile-brand{display:flex;align-items:center;gap:8px;color:var(--di-text);font-size:12px;font-weight:650;line-height:1.08;text-decoration:none}
    .mobile-brand img{flex:none}
    .form-content{width:100%;max-width:470px;flex:none;margin:0 auto;padding:21px;border:1px solid var(--di-border);border-radius:var(--di-radius-dialog);background:var(--di-surface)}
    .form-content>*{width:100%}
    @media(min-width:600px){.form-panel{padding:20px 32px 32px}.form-content{margin-block:auto;padding:30px}.auth-tools{max-width:470px}}
    @media(min-width:960px){
      .auth-layout{display:grid;grid-template-columns:minmax(420px,48%) minmax(0,1fr)}
      .brand-panel{position:relative;isolation:isolate;display:flex;flex-direction:column;min-height:100svh;overflow:hidden;padding:clamp(30px,4vw,62px);background:var(--di-sidebar-background);color:#f1efe8}
      .brand{display:flex;gap:12px;align-items:center;width:max-content;color:#fffdf7;text-decoration:none;font-size:16px;font-weight:650;line-height:1.1}
      .brand img{flex:none}
      .brand-copy{position:relative;z-index:2;max-width:430px;margin:auto 0 290px}
      .brand-copy h1{margin:0 0 16px;font-size:clamp(42px,4.4vw,62px);line-height:.98;letter-spacing:-.055em;font-weight:600}
      .brand-copy p{max-width:37ch;margin:0;color:#d1d8dc;font-size:15px;line-height:1.65}
      .brand-art{position:absolute;z-index:1;right:-8%;bottom:-9%;width:min(94%,740px);height:auto;opacity:.9;pointer-events:none}
      .evidence-route{position:absolute;z-index:3;left:clamp(30px,4vw,62px);bottom:34px;display:flex;align-items:center;gap:0;color:#d4dbe0;font-size:11px}
      .evidence-route span{display:flex;align-items:center;gap:8px;padding-right:16px}
      .evidence-route span+span{padding-left:16px;border-left:1px solid #d1d8dc47}
      .evidence-route i{width:7px;height:7px;border:1px solid var(--di-primary);border-radius:50%;background:var(--di-sidebar-background)}
      .evidence-route span:last-child i{background:var(--di-primary)}
      .form-panel{min-height:100svh;gap:12px;padding:22px clamp(28px,5vw,72px);background:var(--di-background)}
      .mobile-brand{display:none}
      .auth-tools{justify-content:flex-end;max-width:none}
      .form-content{flex:0 1 auto;display:flex;flex-direction:column;justify-content:center;margin:auto;padding:34px;width:min(100%,470px)}
    }
    @media(max-height:720px) and (min-width:960px){.brand-copy{margin-bottom:145px}.brand-copy h1{font-size:clamp(38px,3.7vw,50px);margin-bottom:13px}.brand-art{width:min(86%,640px)}.form-panel{padding-block:13px}.form-content{padding:25px}}
    @media(max-height:620px){.form-panel{min-height:100svh;gap:10px}.form-content{padding:17px}}
    @media(max-width:480px){.form-panel{padding-inline:13px}.form-content{padding:18px;border-radius:var(--di-radius-dialog)}}
    @media(max-width:599px) and (min-height:700px){.form-panel{justify-content:center}}
    @media(prefers-reduced-motion:no-preference){.brand-copy{animation:chart-in 620ms cubic-bezier(.2,.8,.2,1) both}.brand-art{animation:chart-draw 860ms cubic-bezier(.2,.8,.2,1) 90ms both}.form-content{animation:form-arrive 420ms cubic-bezier(.2,.8,.2,1) both}}
    @keyframes chart-in{from{opacity:.65;transform:translateY(8px)}to{opacity:1;transform:translateY(0)}}
    @keyframes chart-draw{from{opacity:0;transform:translate(9px,8px)}to{opacity:.9;transform:translate(0,0)}}
    @keyframes form-arrive{from{opacity:.7;transform:translateY(6px)}to{opacity:1;transform:translateY(0)}}
    @media(prefers-reduced-motion:reduce){.brand-copy,.brand-art,.form-content{animation:none}}
  `,
})
export class AuthFrame {}
