import { SessionApiService } from '../core/session-api.service';
import { Component, inject, input, output } from '@angular/core';
import { navigation, availableNavigation, Page } from '../core/navigation';
import { DiIcon } from './di-icon';
import { TranslatePipe } from '../core/translate.pipe';
@Component({
  selector: 'app-workspace-sidebar',
  imports: [DiIcon, TranslatePipe],
  templateUrl: './workspace-sidebar.html',
  styleUrl: './workspace-sidebar.scss',
})
export class WorkspaceSidebar {
  readonly page = input.required<Page>();
  readonly opened = input(false);
  readonly navigate = output<Page>();
  private readonly api=inject(SessionApiService);
get navigation(){return availableNavigation(this.api.account());}
  go(page: Page): void {
    this.navigate.emit(page);
  }
}
