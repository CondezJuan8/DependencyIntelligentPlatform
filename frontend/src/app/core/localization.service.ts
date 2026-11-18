import { DOCUMENT } from '@angular/common';
import { effect, inject, Injectable } from '@angular/core';
import { PreferencesService } from './preferences.service';
import { english } from './translations';

interface TextState {
  source: string;
  rendered: string;
}

@Injectable({ providedIn: 'root' })
export class LocalizationService {
  private readonly document = inject(DOCUMENT);
  private readonly preferences = inject(PreferencesService);
  private readonly textStates = new WeakMap<Text, TextState>();
  private readonly attributeStates = new WeakMap<Element, Map<string, TextState>>();
  private observer?: MutationObserver;

  constructor() {
    effect(() => {
      this.preferences.language();
      if (this.observer) this.translateTree(this.document.documentElement);
    });
  }

  start(): void {
    if (this.observer) return;
    const Observer = this.document.defaultView?.MutationObserver;
    if (!Observer) return;

    this.observer = new Observer((mutations) => {
      for (const mutation of mutations) {
        if (mutation.type === 'characterData' && mutation.target instanceof Text) {
          this.translateText(mutation.target);
        } else if (mutation.type === 'attributes' && mutation.target instanceof Element) {
          this.translateAttributes(mutation.target);
        } else {
          for (const node of mutation.addedNodes) this.translateTree(node);
        }
      }
    });
    this.observer.observe(this.document.documentElement, {
      subtree: true,
      childList: true,
      characterData: true,
      attributes: true,
      attributeFilter: ['aria-label', 'aria-description', 'placeholder', 'title', 'data-label'],
    });
    this.translateTree(this.document.documentElement);
  }

  private translateTree(root: Node): void {
    if (root.nodeType === 3) {
      if (root instanceof Text) this.translateText(root);
      return;
    }

    const walker = this.document.createTreeWalker(root, 4);
    let node = walker.nextNode();
    while (node) {
      if (node instanceof Text) this.translateText(node);
      node = walker.nextNode();
    }

    if (root instanceof Element) {
      this.translateAttributes(root);
      root.querySelectorAll('*').forEach((element) => this.translateAttributes(element));
    }
  }

  private translateText(node: Text): void {
    if (this.protected(node.parentElement)) return;
    const current = node.data;
    let state = this.textStates.get(node);
    if (!state) {
      state = { source: current, rendered: current };
      this.textStates.set(node, state);
    } else if (current !== state.rendered) {
      state.source = current;
    }

    const next = this.translate(state.source);
    if (next !== current) {
      state.rendered = next;
      node.data = next;
    } else {
      state.rendered = current;
    }
  }

  private translateAttributes(element: Element): void {
    if (this.protected(element)) return;
    const states = this.attributeStates.get(element) ?? new Map<string, TextState>();
    this.attributeStates.set(element, states);
    for (const name of ['aria-label', 'aria-description', 'placeholder', 'title', 'data-label']) {
      const current = element.getAttribute(name);
      if (current === null) continue;
      let state = states.get(name);
      if (!state) {
        state = { source: current, rendered: current };
        states.set(name, state);
      } else if (current !== state.rendered) {
        state.source = current;
      }

      const next = this.translate(state.source);
      if (next !== current) {
        state.rendered = next;
        element.setAttribute(name, next);
      } else {
        state.rendered = current;
      }
    }
  }

  private translate(source: string): string {
    if (this.preferences.language() !== 'en') return source;
    const leading = source.match(/^\s*/)?.[0] ?? '';
    const trailing = source.match(/\s*$/)?.[0] ?? '';
    const phrase = source.trim().replace(/\s+/g, ' ');
    const exact = english[phrase];
    if (exact !== undefined) return `${leading}${exact}${trailing}`;

    const prefix = Object.keys(english)
      .filter((key) => key.endsWith(' ') && phrase.startsWith(key))
      .sort((left, right) => right.length - left.length)[0];
    if (prefix) return `${leading}${english[prefix]}${phrase.slice(prefix.length)}${trailing}`;
    return source;
  }

  private protected(element: Element | null): boolean {
    return !!element?.closest(
      '[data-no-translate],input,textarea,script,style,pre,code,[contenteditable="true"]',
    );
  }
}
