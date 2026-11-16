import { Component, Input } from '@angular/core';
const paths: Record<string, string> = {
  dashboard: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  box: 'm12 3 9 5-9 5-9-5 9-5Z M3 8v9l9 5 9-5V8 M12 13v9 M7.5 5.5l9 5',
  shield: 'm12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z m-4 9 3 3 5-6',
  graph:
    'M6 7l12 3M6 7l4 12m8-9-8 9 M3 7a3 3 0 1 0 6 0a3 3 0 1 0-6 0 M15 10a3 3 0 1 0 6 0a3 3 0 1 0-6 0 M7 19a3 3 0 1 0 6 0a3 3 0 1 0-6 0',
  analysis: 'M4 20h16 M7 16V9 M12 16V4 M17 16v-5',
  document: 'M14 3H5v18h14V8l-5-5Z M14 3v5h5 M8 12h8 M8 16h5',
  report: 'M14 3H5v18h14V8l-5-5Z M14 3v5h5 M8 17v-3 M12 17v-6 M16 17v-2',
  users:
    'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M16 4a4 4 0 0 1 0 8 M22 21v-2a4 4 0 0 0-3-3.9 M5 7a4 4 0 1 0 8 0a4 4 0 1 0-8 0',
  settings:
    'M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8 M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1 1-3Z',
  search: 'M10.5 3a7.5 7.5 0 1 0 0 15a7.5 7.5 0 1 0 0-15 M16 16l5 5',
  menu: 'M4 6h16 M4 12h16 M4 18h16',
  bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4',
  chevron: 'm6 9 6 6 6-6',
  arrow: 'M4 12h16 m-6-6 6 6-6 6',
  plus: 'M12 4v16 M4 12h16',
  close: 'm5 5 14 14 M19 5 5 19',
  mail: 'M3 5h18v14H3V5Z m0 1 9 7 9-7',
  lock: 'M5 10h14v11H5V10Z M8 10V7a4 4 0 0 1 8 0v3',
  person: 'M4 21v-2a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v2 M8 6a4 4 0 1 0 8 0a4 4 0 1 0-8 0',
  eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0',
  check: 'm5 12 4 4L19 6',
  edit: 'm15 4 5 5 M4 15 16 3l5 5L9 20H4v-5Z',
  logout: 'M9 4H4v16h5 M9 12h12 m-4-4 4 4-4 4',
};
@Component({
  selector: 'app-di-icon',
  template:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="path" /></svg>',
  styles:
    ':host { display:inline-flex;width:20px;height:20px;flex-shrink:0;vertical-align:middle; } svg { width:100%;height:100%; }',
})
export class DiIcon {
  @Input() name = 'box';
  get path(): string {
    return paths[this.name] ?? paths['box'];
  }
}
