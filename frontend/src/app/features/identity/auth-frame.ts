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
        <span class="brand-kicker">Un espacio para revisar dependencias</span>
        <h1>Del origen<br />a la decisión.</h1>
        <p>Conserva el vínculo entre el repositorio, cada análisis y la evidencia publicada que revisa tu equipo.</p>
      </div>
      <div class="evidence-route" aria-label="Recorrido de la evidencia">
        <div class="route-step"><span class="route-index">A</span><span><strong>Fuente</strong><small>Repositorio autorizado</small></span></div>
        <span class="route-link" aria-hidden="true"></span>
        <div class="route-step"><span class="route-index">B</span><span><strong>Snapshot</strong><small>Versiones observadas</small></span></div>
        <span class="route-link" aria-hidden="true"></span>
        <div class="route-step"><span class="route-index">C</span><span><strong>OSV</strong><small>Avisos para revisar</small></span></div>
      </div>
      <p class="brand-footnote">Los resultados conservan su estado y cobertura; una consulta pendiente no se presenta como resultado seguro.</p>
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
      <p class="form-footnote">Acceso privado para organizaciones y equipos autorizados.</p>
    </main>
  </div>`,
  styles: `
    :host{display:block;min-height:100svh;background:var(--di-background);color:var(--di-text)}
    .auth-layout{min-height:100svh;display:grid;grid-template-columns:minmax(0,1fr);align-content:start;background:var(--di-background)}
    .brand-panel{display:flex;flex-direction:column;gap:20px;padding:18px 20px 0;background:var(--di-sidebar-background);color:#f1f3f8}
    .brand{display:flex;align-items:center;gap:9px;width:max-content;color:#f7f8fd;text-decoration:none;font-size:12px;font-weight:650;line-height:1.05}
    .brand img{width:28px;height:32px;flex:none}
    .brand-copy{max-width:560px}
    .brand-kicker{display:block;margin-bottom:7px;color:#aeb8cc;font-size:10px;font-weight:600;letter-spacing:.04em}
    .brand-copy h1{margin:0 0 8px;font-size:clamp(23px,5vw,30px);line-height:1.02;letter-spacing:-.045em;font-weight:600}
    .brand-copy p{max-width:52ch;margin:0;color:#c8cedd;font-size:12px;line-height:1.5}
    .evidence-route{display:flex;align-items:center;gap:8px;max-width:660px;padding:0 0 14px}
    .route-step{display:flex;align-items:center;gap:8px;min-width:0}
    .route-index{display:grid;place-items:center;width:23px;height:23px;flex:none;border:1px solid #7d89a0;color:#eef1f6;font-size:9px;font-weight:600}
    .route-step strong,.route-step small{display:block;white-space:nowrap}
    .route-step strong{font-size:10px;font-weight:600}
    .route-step small{margin-top:2px;color:#aeb8cc;font-size:9px}
    .route-link{height:1px;min-width:12px;flex:1;background:#6e789055}
    .brand-footnote,.form-footnote{display:none}
    .form-panel{min-height:0;display:flex;flex-direction:column;gap:14px;padding:12px 20px 26px;background:var(--di-background)}
    .auth-tools{display:flex;align-items:center;justify-content:space-between;gap:10px;width:100%;max-width:470px;margin:0 auto}
    .mobile-brand{display:none;align-items:center;gap:8px;color:var(--di-text);font-size:12px;font-weight:650;line-height:1.08;text-decoration:none}
    .mobile-brand img{flex:none}
    .form-content{width:100%;max-width:470px;flex:none;margin:0 auto;padding:0;background:transparent}
    .form-content>*{width:100%}
    .form-content:focus-within{outline:none}
    @media(min-width:620px){.brand-panel{padding:23px 32px 4px}.brand-copy{margin-inline:auto;width:100%}.evidence-route{width:min(100%,660px);margin-inline:auto;padding-bottom:18px}.form-panel{padding:12px 32px 34px}.auth-tools{max-width:470px}.form-content{margin-block:auto}}
    @media(min-width:960px){
      .auth-layout{grid-template-columns:minmax(380px,.82fr) minmax(510px,1.18fr);min-height:100svh}
      .brand-panel{position:sticky;top:0;box-sizing:border-box;min-height:100svh;padding:clamp(30px,4vw,62px);gap:0}
      .brand{font-size:15px}
      .brand img{width:36px;height:42px}
      .brand-copy{max-width:440px;margin:auto 0 72px}
      .brand-kicker{margin-bottom:16px;font-size:11px;letter-spacing:.06em}
      .brand-copy h1{margin-bottom:17px;font-size:clamp(45px,5.1vw,72px);line-height:.96;letter-spacing:-.055em}
      .brand-copy p{max-width:42ch;font-size:15px;line-height:1.65}
      .evidence-route{width:100%;max-width:550px;margin:0 0 19px;padding:0}
      .route-step{gap:10px}.route-index{width:30px;height:30px;font-size:10px}.route-step strong{font-size:12px}.route-step small{font-size:10px}
      .brand-footnote{display:block;max-width:420px;margin:0;color:#aeb8cc;font-size:11px;line-height:1.55}
      .form-panel{box-sizing:border-box;min-height:100svh;gap:0;padding:28px clamp(34px,6vw,88px) 24px}
      .auth-tools{max-width:none;justify-content:flex-end}
      .form-content{flex:0 1 auto;display:flex;flex-direction:column;justify-content:center;margin:auto;width:min(100%,500px)}
      .form-footnote{display:block;width:min(100%,500px);margin:0 auto;color:var(--di-text-subtle);font-size:11px;text-align:center}
    }
    @media(max-height:700px) and (min-width:960px){.brand-copy{margin-bottom:42px}.brand-copy h1{font-size:clamp(40px,4vw,58px)}.form-panel{padding-block:15px}.form-content{padding-block:14px}}
    @media(max-width:959px){.auth-tools{max-width:620px;justify-content:flex-end}.form-panel{min-height:auto}.form-content{margin:0 auto}}
    @media(max-width:480px){.brand-panel{padding:14px 16px 0;gap:11px}.brand-copy p{max-width:40ch}.evidence-route{gap:5px}.route-step{gap:5px}.route-step small{font-size:8px}.route-index{width:21px;height:21px}.route-link{min-width:7px}.form-panel{padding:10px 16px 25px}.form-content{margin-top:0}}
    @media(prefers-reduced-motion:no-preference){.brand-copy{animation:arrive 420ms cubic-bezier(.2,.8,.2,1) both}.form-content{animation:arrive 360ms cubic-bezier(.2,.8,.2,1) 50ms both}}
    @keyframes arrive{from{opacity:.7;transform:translateY(5px)}to{opacity:1;transform:translateY(0)}}
    @media(prefers-reduced-motion:reduce){.brand-copy,.form-content{animation:none}}
  `,
})
export class AuthFrame {}
