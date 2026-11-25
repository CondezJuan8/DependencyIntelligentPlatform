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
  get navigationGroups() {
    const available = this.navigation;
    const groups: { label: string; ids: Page[] }[] = [
      { label: 'Espacio', ids: ['dashboard', 'repositories'] },
      { label: 'Analizar', ids: ['analysis', 'inventory'] },
      { label: 'Revisar', ids: ['vulnerabilities', 'reports'] },
      { label: 'Conocimiento', ids: ['documentation', 'assistant'] },
      { label: 'Equipo y cuenta', ids: ['organization', 'administration', 'settings'] },
    ];
    return groups.flatMap(group => {
      const items = group.ids.flatMap(id => {
        const item = available.find(candidate => candidate.id === id);
        return item ? [item] : [];
      });
      return items.length ? [{ label: group.label, items }] : [];
    });
  }
  go(page: Page): void {
    this.navigate.emit(page);
  }
}
