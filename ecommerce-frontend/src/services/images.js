import { toFileUrl } from './api';

const splitPattern = /[\n,，|;]+/;

const clean = (value) => String(value || '').trim();

export const parseImageUrls = (value) => {
  if (Array.isArray(value)) {
    return value.map(clean).filter(Boolean);
  }

  const text = clean(value);
  if (!text) {
    return [];
  }

  try {
    const parsed = JSON.parse(text);
    if (Array.isArray(parsed)) {
      return parsed.map(clean).filter(Boolean);
    }
    if (typeof parsed === 'string') {
      const single = clean(parsed);
      return single ? [single] : [];
    }
  } catch {
    // fall through to plain-text parsing
  }

  return text
    .split(splitPattern)
    .map(clean)
    .filter(Boolean);
};

export const resolveImageUrls = (value) => parseImageUrls(value).map((url) => toFileUrl(url));

export const resolveCoverImageUrl = (value) => resolveImageUrls(value)[0] || '';

export const serializeImageUrls = (value) => JSON.stringify(parseImageUrls(value));

void resolveCoverImageUrl;
void serializeImageUrls;


