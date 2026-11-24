import { Component, inject, input, output, signal } from '@angular/core';
import { A11yModule } from '@angular/cdk/a11y';
import { PreferencesService } from '../core/preferences.service';
import { AssistantWorkspace } from '../features/assistant/assistant-workspace';
import { DiIcon } from './di-icon';

type DrawerSide = 'left' | 'right';

@Component({ selector:'app-assistant-panel', imports:[A11yModule,AssistantWorkspace,DiIcon], templateUrl:'./assistant-panel.html', styleUrl:'./assistant-panel.scss' })
export class AssistantPanel {
  readonly preferences=inject(PreferencesService);
  readonly opened=input(false);
  readonly closed=output<void>();
  readonly side=signal<DrawerSide>(this.readSide());
  close():void { this.closed.emit(); }
  moveToOtherSide():void { const next:DrawerSide=this.side()==='right'?'left':'right';this.side.set(next);try{localStorage.setItem('di.assistant.side',next);}catch{/* Browser storage can be unavailable. */} }
  private readSide():DrawerSide { try{return localStorage.getItem('di.assistant.side')==='left'?'left':'right';}catch{return 'right';} }
}
