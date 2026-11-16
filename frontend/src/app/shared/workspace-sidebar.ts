import { Component, input, output } from '@angular/core';
import { navigation, Page } from '../core/navigation';
import { DiIcon } from './di-icon';
@Component({
  selector: 'app-workspace-sidebar',
  imports: [DiIcon],
  templateUrl: './workspace-sidebar.html',
  styleUrl: './workspace-sidebar.scss',
})
export class WorkspaceSidebar {
  readonly page = input.required<Page>();
  readonly opened = input(false);
  readonly navigate = output<Page>();
  readonly navigation = navigation;
  go(page: Page): void {
    this.navigate.emit(page);
  }
}
