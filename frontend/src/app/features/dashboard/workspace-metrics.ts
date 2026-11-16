import { Component, input } from '@angular/core';
import { DiIcon } from '../../shared/di-icon';
@Component({
  selector: 'app-workspace-metrics',
  imports: [DiIcon],
  templateUrl: './workspace-metrics.html',
  styleUrl: './workspace-metrics.scss',
})
export class WorkspaceMetrics {
  readonly count = input(0);
  readonly loading = input(false);
  readonly failed = input(false);
}
