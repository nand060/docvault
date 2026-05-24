const CONTROL_CHARS = /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f-\u009f]/g;
const PATH_TRAVERSAL = /(?:^|[\\/])\.\.(?:[\\/]|$)|\.\.[\\/]/g;
const HTML_TAG = /<[^>]*>/g;
const TEMPLATE_DELIMITER = /[{}]/g;
const DANGEROUS_FILENAME_CHARS = /[<>"'`]/g;
const EDGE_WHITESPACE = /^[\s\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+|[\s\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+$/g;

export function sanitizeText(text) {
  return String(text ?? '')
    .normalize('NFC')
    .replace(CONTROL_CHARS, '')
    .replace(/\u0000/g, '')
    .replace(EDGE_WHITESPACE, '');
}

export function sanitizeFilename(name) {
  const sanitized = sanitizeText(name)
    .replace(PATH_TRAVERSAL, '')
    .replace(/[\\/]+/g, '_')
    .replace(HTML_TAG, '')
    .replace(TEMPLATE_DELIMITER, '')
    .replace(DANGEROUS_FILENAME_CHARS, '')
    .replace(/\s+/g, ' ')
    .replace(EDGE_WHITESPACE, '');

  return sanitized || 'upload.txt';
}
