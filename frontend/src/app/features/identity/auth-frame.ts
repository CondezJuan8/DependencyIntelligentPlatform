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
    </aside>
    <main class="form-panel">
      <div class="auth-tools">
        <div class="mobile-brand">
          <img src="/brand-mark.svg" alt="" width="26" height="30" />
          <span>Dependency<br />Intelligence</span>
        </div>
        <app-preference-controls />
      </div>
      <div class="form-content"><ng-content /></div>
    </main>
  </div>`,
  styles: `
    :host{display:block;min-height:100svh}
    .auth-layout{min-height:100svh;display:flex;flex-direction:column;background:var(--di-background)}
    .brand-panel{display:none}
    .form-panel{min-height:100svh;display:flex;flex-direction:column;padding:14px 18px;background:var(--di-surface)}
    .auth-tools{display:flex;align-items:center;justify-content:space-between;gap:12px;width:100%;max-width:440px;margin:0 auto}
    .mobile-brand{display:flex;align-items:center;gap:8px;font-weight:600;font-size:13px;line-height:1.15;color:var(--di-text)}
    .form-content{width:100%;max-width:440px;flex:none;margin:0 auto;padding:clamp(28px,7svh,64px) 0 28px}
    .form-content>*{width:100%}
    @media(min-width:600px){.form-panel{padding:20px 36px}.form-content{padding-top:clamp(36px,8svh,76px)}}
    @media(min-width:960px){
      .auth-layout{display:grid;grid-template-columns:minmax(330px,42%) minmax(0,1fr)}
      .brand-panel{display:flex;flex-direction:column;min-height:100svh;padding:clamp(32px,4vw,64px);background:var(--di-navy);color:#fff}
      .brand{display:flex;gap:13px;align-items:center;color:#fff;text-decoration:none;font-size:19px;font-weight:600;line-height:1.15}
      .brand-copy{max-width:440px;margin:auto 0}
      .brand-copy h1{margin:0 0 20px;font-size:clamp(36px,3.6vw,48px);line-height:1.1;letter-spacing:-1.1px;font-weight:600}
      .brand-copy p{max-width:360px;margin:0;color:#d2deed;font-size:15px;line-height:1.65}
      .form-panel{padding:18px 44px}
      .mobile-brand{display:none}
      .auth-tools{justify-content:flex-end;max-width:none}
      .form-content{flex:1;display:flex;align-items:center;margin:auto;padding:0}
    }
    @media(max-height:620px){.form-panel{min-height:100svh}.form-content{padding-top:20px;padding-bottom:20px}}
    @media(min-width:960px) and (max-height:700px){.brand-panel{padding:28px 40px}.brand-copy h1{font-size:38px;margin-bottom:14px}.brand-copy p{font-size:14px}.form-panel{padding-inline:32px}}
  `,
})
export class AuthFrame {}
