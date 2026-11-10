import type { ReadingProfile } from '../types/profile';

// Compiles and downloads the active reading profile as a JSON file compatible with the C++ app
export function exportProfileJson(profile: ReadingProfile): void {
  const jsonString = JSON.stringify(profile, null, 2);
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