import { Injectable, signal } from '@angular/core';
import { english } from './translations';
export type Language = 'es'|'en';
export type Theme = 'light'|'dark'|'system';
@Injectable({providedIn:'root'})
export class PreferencesService {
  readonly language=signal<Language>('es');
  readonly theme=signal<Theme>('system');
  private readonly media=window.matchMedia('(prefers-color-scheme: dark)');
  constructor(){
    try{const language=localStorage.getItem('di.language');const theme=localStorage.getItem('di.theme');if(language==='en'||language==='es')this.language.set(language);if(theme==='light'||theme==='dark'||theme==='system')this.theme.set(theme);}catch{}
    this.apply();this.media.addEventListener('change',()=>this.apply());
  }
  setLanguage(value:string):void{if(value!=='es'&&value!=='en')return;this.language.set(value);try{localStorage.setItem('di.language',value);}catch{}this.apply();}
  setTheme(value:string):void{if(!['light','dark','system'].includes(value))return;this.theme.set(value as Theme);try{localStorage.setItem('di.theme',value);}catch{}this.apply();}
  cycleTheme():void{this.setTheme(this.theme()==='system'?'light':this.theme()==='light'?'dark':'system');}
  text(source:unknown):string{if(source==null)return '';const value=String(source);if(this.language()==='es')return value;return english[value]??english[value.trim()]??value;}
  private apply():void{document.documentElement.lang=this.language();document.documentElement.dataset['theme']=this.theme()==='system'?(this.media.matches?'dark':'light'):this.theme();}
}
