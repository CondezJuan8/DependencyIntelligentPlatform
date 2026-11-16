import { Component } from '@angular/core';
@Component({
  selector: 'app-auth-frame',
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
      <div class="brand-footer"><span></span>Claridad en cada dependencia.</div>
    </aside>
    <main class="form-panel">
      <div class="mobile-brand">
        <img src="/brand-mark.svg" alt="" width="28" height="32" />Dependency Intelligence
      </div>
      <div class="form-content"><ng-content /></div>
      <p class="form-footer">Dependency Intelligence · Tu software, con perspectiva.</p>
    </main>
  </div>`,
  styles: `
    :host {
      display: block;
    }
    .auth-layout {
      min-height: 100svh;
      display: grid;
      grid-template-columns: minmax(360px, 46%) 1fr;
    }
    .brand-panel {
      position: relative;
      overflow: hidden;
      background:
        radial-gradient(ellipse at 85% 90%, #063d70 0%, transparent 60%),
        linear-gradient(145deg, #071322, var(--di-navy) 65%, #06213b);
      padding: clamp(36px, 4.4vw, 72px);
      color: white;
      display: flex;
      flex-direction: column;
      min-height: 700px;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 18px;
      color: white;
      text-decoration: none;
      font-size: 25px;
      font-weight: 650;
      line-height: 1.12;
      letter-spacing: -0.6px;
      position: relative;
      z-index: 1;
    }
    .brand-copy {
      position: relative;
      z-index: 1;
      margin: clamp(45px, 7vh, 90px) 0 150px;
      max-width: 460px;
    }
    .eyebrow {
      font-size: 10px;
      font-weight: 600;
      letter-spacing: 2.5px;
      color: #8cc9ed;
    }
    h1 {
      font-size: clamp(42px, 4vw, 62px);
      line-height: 1.07;
      letter-spacing: -2px;
      margin: 22px 0;
      font-weight: 700;
    }
    h1 span {
      color: #63d8f5;
    }
    .brand-copy p {
      font-size: 17px;
      line-height: 1.75;
      color: #d0e1f3;
      max-width: 355px;
      margin: 0;
    }
    .network {
      position: absolute;
      width: 115%;
      max-width: none;
      bottom: -15px;
      left: -5%;
      opacity: 0.85;
      pointer-events: none;
    }
    .brand-footer {
      position: relative;
      z-index: 1;
      display: flex;
      align-items: center;
      gap: 10px;
      color: #a6c1da;
      font-size: 12px;
      margin-top: auto;
    }
    .brand-footer span {
      background: var(--di-accent);
      border-radius: 50%;
      width: 6px;
      height: 6px;
      box-shadow: 0 0 12px var(--di-accent);
    }
    .form-panel {
      padding: 42px clamp(28px, 6vw, 100px) 24px;
      background:
        radial-gradient(ellipse at 100% 0%, var(--di-info-background) 0%, transparent 55%),
        var(--di-white);
      display: flex;
      flex-direction: column;
      justify-content: center;
    }
    .form-content {
      width: 100%;
      max-width: 455px;
      margin: auto;
      padding: 24px 0;
    }
    .form-footer {
      color: var(--di-text-subtle);
      font-size: 11px;
      text-align: center;
      margin-top: 28px;
    }
    .mobile-brand {
      display: none;
    }
    @media (min-width: 1450px) {
      .form-content {
        max-width: 490px;
      }
    }
    @media (max-width: 950px) {
      .brand-panel {
        padding: 40px 32px;
      }
      .brand {
        font-size: 22px;
      }
      .brand-copy {
        margin-top: 80px;
      }
      h1 {
        font-size: 46px;
      }
      .form-panel {
        padding: 32px;
      }
    }
    @media (max-width: 730px) {
      .auth-layout {
        display: block;
      }
      .brand-panel {
        display: none;
      }
      .form-panel {
        min-height: 100svh;
        padding: 28px 24px 18px;
      }
      .mobile-brand {
        display: flex;
        align-items: center;
        gap: 10px;
        font-weight: 650;
        color: var(--di-navy);
        font-size: 15px;
      }
      .form-content {
        padding: 36px 0;
      }
    }
  `,
})
export class AuthFrame {}
