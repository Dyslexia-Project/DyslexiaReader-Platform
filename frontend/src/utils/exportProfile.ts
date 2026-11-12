import type { ReadingProfile } from '../types/profile';
import { toCanonicalProfile } from '../types/profile';

// Compiles and downloads the active reading profile in canonical schema v1 JSON
// compatible with both DyslexiaReader-Desktop and Spring Boot /api/v1/profiles/share.
export function exportProfileJson(profile: ReadingProfile): void {
  const canonical = toCanonicalProfile(profile);
  const jsonString = JSON.stringify(canonical, null, 2) + '\n';
  const blob = new Blob([jsonString], { type: 'application/json' });
  const url = URL.createObjectURL(blob);

  const link = document.createElement('a');
  link.href = url;
  link.download = 'reading-profile.json';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
