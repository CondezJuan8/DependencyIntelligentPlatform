/** Keep OSV's wording while removing presentation markup from descriptions. */
export function plainOsvText(details: string): string {
  return details
    .replace(/<details\b[^>]*>[\s\S]*?<\/details\s*>/gi, '')
    .replace(/<\/?(?:summary|h[1-6]|p|div|br|ul|ol|li|pre|code|blockquote)\b[^>]*>/gi, '\n')
    .replace(/\r\n?/g, '\n')
    .replace(/^[ \t]{0,3}#{1,6}[ \t]+/gm, '')
    .replace(/^[ \t]{0,3}(?:[-*+]|\d+\.)[ \t]+/gm, '')
    .replace(/^[ \t]{0,3}>[ \t]?/gm, '')
    .replace(/!?\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/`{1,3}|\*\*|__/g, '')
    .replace(/&(?:amp|lt|gt|quot|apos|nbsp);/gi, entity => ({
      '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&apos;': "'", '&nbsp;': ' ',
    })[entity.toLowerCase()] ?? entity)
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}
