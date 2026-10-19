export function visible(element, win) {
  for (let current = element; current; current = current.parentElement) {
    const style = win.getComputedStyle(current);
    if (current.hidden || current.getAttribute('aria-hidden') === 'true' || style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
  }
  return element.getClientRects().length > 0;
}
export function privacyVerified(doc, win, depth = 0) {
  if (depth > 3) return false;
  for (const frame of doc.querySelectorAll('iframe')) {
    if (!visible(frame, win)) continue;
    try {
      if (!frame.contentDocument || !frame.contentWindow || !privacyVerified(frame.contentDocument, frame.contentWindow, depth + 1)) return false;
    } catch { return false; }
  }
  return true;
}
export function privacyProbe(doc, win, depth = 0) {
  if (depth > 3) return true;
  const sensitive = 'input[type="password"],input[autocomplete*="one-time-code"],input[autocomplete*="cc-"],[role="log"],[data-focuson-sensitive],form[action*="payment"],form[action*="login"]';
  if ([...doc.querySelectorAll(sensitive)].some(element => visible(element, win))) return true;
  if ([...doc.querySelectorAll('[contenteditable="true"],textarea')].some(element => {
    const label = `${element.getAttribute('aria-label') ?? ''} ${element.getAttribute('placeholder') ?? ''} ${element.getAttribute('name') ?? ''}`;
    return visible(element, win) && /message|compose|email|recipient|메시지|메일|받는\s*사람/i.test(label);
  })) return true;
  return [...doc.querySelectorAll('iframe')].some(frame => {
    if (!visible(frame, win)) return false;
    try { return frame.contentDocument && privacyProbe(frame.contentDocument, frame.contentWindow, depth + 1); }
    catch { return false; }
  });
}
export function collectDom(doc, win, limit = 6000) {
  const root = doc.querySelector('main,[role="main"],article') ?? doc.body;
  if (!root) return { text: '', complete: true };
  const chunks = [];
  let count = 0;
  const excluded = 'nav,aside,header,footer,form,input,textarea,select,button,script,style,noscript,iframe,[contenteditable="true"],[data-focuson-overlay],[data-focuson-sensitive]';
  const read = node => {
    if (node.nodeType === 3) return (node.nodeValue ?? '').slice(0, limit + 1);
    if (node.nodeType !== 1 || node.matches(excluded) || !visible(node, win)) return '';
    if (node.tagName === 'BR') return '\n';
    let text = '';
    for (const child of node.childNodes) {
      text += read(child);
      if (text.length > limit) return text.slice(0, limit + 1);
    }
    return text;
  };
  for (const element of root.querySelectorAll('h1,h2,h3,p,pre,li,table,figcaption')) {
    if (element.closest(excluded) || !visible(element, win)) continue;
    if (element.parentElement?.closest('pre,table,li')?.closest('main,article,[role="main"],body')) continue;
    let text = read(element).trim();
    if (element.tagName === 'TABLE') text = [...element.querySelectorAll('tr')].filter(row => visible(row, win)).map(row => [...row.querySelectorAll('th,td')].map(cell => read(cell).trim()).join('\t')).join('\n');
    if (!text) continue;
    if (/^H[123]$/.test(element.tagName)) text = `${'#'.repeat(Number(element.tagName[1]))} ${text}`;
    if (element.tagName === 'PRE') {
      const language = element.querySelector('code')?.className.match(/(?:^|\s)language-([a-z0-9#+-]+)/i)?.[1] ?? '';
      const fence = '`'.repeat(Math.max(3, ...[...text.matchAll(/`+/g)].map(match => match[0].length + 1)));
      text = `${fence}${language}\n${text}\n${fence}`;
    }
    count += text.length + (chunks.length ? 2 : 0);
    if (count > limit) return { text: '', complete: false };
    chunks.push(text);
  }
  return { text: chunks.join('\n\n'), complete: true };
}
export function overlayMasks(doc, win) {
  const masks = [];
  for (const element of doc.querySelectorAll('[data-focuson-overlay]')) {
    if (!visible(element, win)) continue;
    const rect = element.getBoundingClientRect();
    if (![rect.x, rect.y, rect.width, rect.height].every(Number.isFinite)) return null;
    masks.push({ x: rect.x, y: rect.y, width: rect.width, height: rect.height });
  }
  return masks;
}
