import { Component } from '@angular/core';
import { PreferenceControls } from '../../shared/preference-controls';
@Component({
  selector: 'app-auth-frame',
  imports:[PreferenceControls],
  template: `<div class="auth-layout">
    <aside class="brand-panel" aria-label="Dependency Intelligence">
      <a class="brand" href="/"
        ><img src="/brand-mark.svg" alt="" width="55" height="62" /><span
          >Dependency<br />Intelligence</span
        ></a
      >
      <div class="brand-copy">
        <span class="eyebrow">CONOCE LO QUE CONSTRUYES</span>
        <h1>Comprende.<br />Anticipa.<br /><span>Protege.</span></h1>
        <p>
          Una nueva perspectiva sobre tus dependencias, sus riesgos y el impacto de cada cambio.
        </p>
      </div>
      <img class="network" src="/auth-network.svg" alt="" aria-hidden="true" />
    </aside>
    <main class="form-panel">
      <div class="auth-tools"><div class="mobile-brand"><img src="/brand-mark.svg" alt="" width="26" height="30"><span>Dependency<br>Intelligence</span></div><app-preference-controls /></div>
      <div class="form-content"><ng-content /></div>
    </main>
  </div>`,
  styles: `
    :host{display:block}.auth-layout{height:100svh;min-height:100svh;display:flex;flex-direction:column}.brand-panel{display:none}.form-panel{height:100svh;min-height:0;display:flex;flex-direction:column;padding:16px;background:var(--di-surface)}.auth-tools{display:flex;align-items:center;justify-content:space-between;gap:10px;width:100%;max-width:440px;margin:0 auto}.mobile-brand{display:flex;align-items:center;gap:8px;font-weight:600;font-size:13px;line-height:1.15;color:var(--di-text)}.form-content{width:100%;max-width:440px;flex:1;min-height:0;display:flex;align-items:center;margin:auto;padding:8px 0}.form-content>*{width:100%}
    @media(min-width:600px){.form-panel{padding:24px 36px}.form-content{padding:16px 0}}
    @media(min-width:960px){.auth-layout{display:grid;grid-template-columns:44% minmax(0,1fr)}.brand-panel{display:flex;flex-direction:column;position:relative;overflow:clip;min-height:100svh;padding:clamp(32px,4vw,64px);background:radial-gradient(ellipse at 85% 90%,#063d70,transparent 65%),linear-gradient(145deg,#071322,#101827 65%,#06213b);color:#fff}.brand{display:flex;gap:16px;align-items:center;font-size:25px;font-weight:600;color:#fff;text-decoration:none;line-height:1.15;z-index:1}.brand-copy{position:relative;z-index:1;margin-top:clamp(36px,7vh,90px);max-width:420px}.eyebrow{color:#a4cae5;font-size:10px;letter-spacing:1.7px}h1{font-size:clamp(38px,4.2vw,60px);line-height:1.08;letter-spacing:-1.3px;margin:20px 0}h1 span{color:#63d8f5}.brand-copy p{font-size:16px;line-height:1.6;color:#d0e1f3;max-width:350px;margin:0}.network{position:absolute;width:115%;left:-5%;bottom:0;pointer-events:none;opacity:.7}.form-panel{padding:22px 46px}.mobile-brand{display:none}.auth-tools{justify-content:flex-end}.form-content{padding:12px 0}}
    @media(min-width:960px) and (max-height:700px){.brand-panel{padding:28px 40px}.brand-copy{margin-top:26px}h1{font-size:40px;margin:14px 0}.brand-copy p{font-size:14px}.form-panel{padding:16px 36px}}
  `,
})
export class AuthFrame {}
