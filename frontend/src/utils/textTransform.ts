// Transforms text for Bionic Reading by highlighting the first half of each word
export function applyBionicReading(text: string): string {
  return text
    .split(' ')
    .map((word) => {
      if (word.length <= 1) return `<strong>${word}</strong>`;
      const mid = Math.ceil(word.length / 2);
      const boldPart = word.slice(0, mid);
      const restPart = word.slice(mid);
      return `<strong>${boldPart}</strong>${restPart}`;
    })
    .join(' ');
}

// Simple syllable splitting using vowel pattern matching for live web preview
export function applySyllableSplit(text: string): string {
  return text.replace(/([aeiouyAEIOUY])([^aeiouy\s]{1,2})([aeiouyAEIOUY])/g, '$1·$2$3');
}