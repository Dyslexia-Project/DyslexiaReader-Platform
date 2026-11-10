export type FontFamily = 'OpenDyslexic' | 'Lexend' | 'Atkinson Hyperlegible' | 'Sans-Serif';
export type ColorTheme = 'light' | 'sepia' | 'dark' | 'high-contrast';

export interface ReadingProfile {
  fontFamily: FontFamily;
  fontSize: number;       // in px (12 - 36)
  letterSpacing: number;  // in px (0 - 8)
  wordSpacing: number;    // in px (0 - 15)
  lineHeight: number;     // multiplier (1.0 - 3.0)
  theme: ColorTheme;
  bionicReading: boolean;
  readingRuler: boolean;
  syllableSplit: boolean;
}

export const DEFAULT_PROFILE: ReadingProfile = {
  fontFamily: 'Lexend',
  fontSize: 18,
  letterSpacing: 1,
  wordSpacing: 2,
  lineHeight: 1.6,
  theme: 'light',
  bionicReading: false,
  readingRuler: false,
  syllableSplit: false,
};