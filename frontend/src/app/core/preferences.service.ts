import { computed, effect, Injectable, signal } from '@angular/core';
import { english } from './translations';
export type Language = 'es'|'en';
export type Theme = 'light'|'dark'|'system';
@Injectable({providedIn:'root'})
export class PreferencesService {
  private readonly personalLanguage=signal<Language>('es');
  private readonly personalTheme=signal<Theme>('system');
  private readonly organizationLanguage=signal<Language|null>(null);
  private readonly organizationTheme=signal<Theme|null>(null);
  private readonly headerLanguageOverride=signal<Language|null>(null);
  private readonly headerThemeOverride=signal<Theme|null>(null);
  readonly language=computed(()=>this.headerLanguageOverride()??this.organizationLanguage()??this.personalLanguage());
  readonly theme=computed(()=>this.headerThemeOverride()??this.organizationTheme()??this.personalTheme());
  readonly personalLanguageValue=computed(()=>this.personalLanguage());
  readonly personalThemeValue=computed(()=>this.personalTheme());
  private readonly media=window.matchMedia('(prefers-color-scheme: dark)');
  constructor(){
    try{const language=localStorage.getItem('di.language');const theme=localStorage.getItem('di.theme');const headerLanguage=localStorage.getItem('di.header.language');const headerTheme=localStorage.getItem('di.header.theme');if(language==='en'||language==='es')this.personalLanguage.set(language);if(theme==='light'||theme==='dark'||theme==='system')this.personalTheme.set(theme);if(headerLanguage==='en'||headerLanguage==='es')this.headerLanguageOverride.set(headerLanguage);if(headerTheme==='light'||headerTheme==='dark'||headerTheme==='system')this.headerThemeOverride.set(headerTheme);}catch{}
    effect(()=>this.apply());this.media.addEventListener('change',()=>this.apply());
  }
  setLanguage(value:string):void{if(value!=='es'&&value!=='en')return;this.personalLanguage.set(value);try{localStorage.setItem('di.language',value);}catch{}}
  setTheme(value:string):void{if(!['light','dark','system'].includes(value))return;this.personalTheme.set(value as Theme);try{localStorage.setItem('di.theme',value);}catch{}}
  setHeaderLanguage(value:string):void{if(value!=='es'&&value!=='en')return;this.setLanguage(value);this.headerLanguageOverride.set(value);try{localStorage.setItem('di.header.language',value);}catch{}}
  cycleHeaderTheme():void{const value=this.theme()==='system'?'light':this.theme()==='light'?'dark':'system';this.setTheme(value);this.headerThemeOverride.set(value);try{localStorage.setItem('di.header.theme',value);}catch{}}
  setWorkspace(theme:Theme,language:Language):void{this.organizationTheme.set(theme);this.organizationLanguage.set(language);}
  clearWorkspace():void{this.organizationTheme.set(null);this.organizationLanguage.set(null);}
  cycleTheme():void{this.setTheme(this.theme()==='system'?'light':this.theme()==='light'?'dark':'system');}
  text(source:unknown):string{if(source==null)return '';const value=String(source);if(this.language()==='es')return value;const trimmed=value.trim();return english[value]??english[trimmed]??english[this.repairUtf8Mojibake(value)]??english[this.repairUtf8Mojibake(trimmed)]??value;}
  private repairUtf8Mojibake(value:string):string{try{const bytes=Uint8Array.from(value,character=>character.charCodeAt(0));return new TextDecoder('utf-8',{fatal:true}).decode(bytes);}catch{return value;}}
  private apply():void{document.documentElement.lang=this.language();document.documentElement.dataset['theme']=this.theme()==='system'?(this.media.matches?'dark':'light'):this.theme();}
}
