import { Component, inject, signal } from '@angular/core';
import { A11yModule } from '@angular/cdk/a11y';
import { PreferencesService } from '../../core/preferences.service';
import { AssistantWorkspace } from '../assistant/assistant-workspace';
import { DiIcon } from '../../shared/di-icon';

type DrawerSide = 'left' | 'right';

@Component({
  selector: 'app-assistant-preview',
  imports: [A11yModule, AssistantWorkspace, DiIcon],
  templateUrl: './assistant-preview.html',
  styleUrl: './assistant-preview.scss',
})
export class AssistantPreview {
  readonly preferences = inject(PreferencesService);
  readonly opened = signal(false);
  readonly side = signal<DrawerSide>(this.readSide());

  open(): void { this.opened.set(true); }
  close(): void { this.opened.set(false); }

  moveToOtherSide(): void {
    const next: DrawerSide = this.side() === 'right' ? 'left' : 'right';
    this.side.set(next);
    try { localStorage.setItem('di.assistant.side', next); } catch { /* Browser storage may be disabled. */ }
  }

  private readSide(): DrawerSide {
    try { return localStorage.getItem('di.assistant.side') === 'left' ? 'left' : 'right'; }
    catch { return 'right'; }
  }
}
