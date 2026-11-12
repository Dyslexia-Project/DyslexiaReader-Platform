export type FontFamily = 'Lexend' | 'Atkinson Hyperlegible' | 'OpenDyslexic';
export type ColorTheme = 'warm-cream' | 'sepia' | 'dark-high-contrast';

export interface ReadingAids {
  bionicReading: boolean;
  readingRuler: boolean;
  bimodalHighlighting: boolean;
}

export interface ReadingProfile {
  schemaVersion: 1;
  fontFamily: FontFamily;
  fontSizePt: number;       // 14 - 48 pt
  letterSpacingEm: number;  // 0 - 0.2 em
  wordSpacingEm: number;    // 0 - 0.5 em
  lineHeight: number;       // 1.0 - 2.5
  lineWidthCh: number;      // 35 - 100 ch
  theme: ColorTheme;
  aids: ReadingAids;
}

export interface PresetSummary {
  displayName: string;
  profile: ReadingProfile;
}

export interface ThemePalette {
  label: string;
  background: string;
  text: string;
  surface: string;
  border: string;
  focus: string;
  sentenceHighlight: string;
  wordHighlightBorder: string;
  rulerHighlight: string;
}

export const COLOR_THEME_PALETTES: Record<ColorTheme, ThemePalette> = {
  'warm-cream': {
    label: 'Warm Cream (Recommended)',
    background: '#FFFDD0',
    text: '#111111',
    surface: '#FFF8E7',
    border: '#70644D',
    focus: '#155EEF',
    sentenceHighlight: '#DCEBFF',
    wordHighlightBorder: '#155EEF',
    rulerHighlight: '#E4E9B4',
  },
  sepia: {
    label: 'Sepia',
    background: '#F4ECD8',
    text: '#3B3026',
    surface: '#EDE1C8',
    border: '#75634F',
    focus: '#174EA6',
    sentenceHighlight: '#D9E7ED',
    wordHighlightBorder: '#174EA6',
    rulerHighlight: '#E1D7B9',
  },
  'dark-high-contrast': {
    label: 'Dark High Contrast',
    background: '#121212',
    text: '#F5F5F5',
    surface: '#242424',
    border: '#A0A0A0',
    focus: '#FFD166',
    sentenceHighlight: '#263B53',
    wordHighlightBorder: '#FFD166',
    rulerHighlight: '#333D2B',
  },
};

export const DEFAULT_PROFILE: ReadingProfile = {
  schemaVersion: 1,
  fontFamily: 'Lexend',
  fontSizePt: 20,
  letterSpacingEm: 0.02,
  wordSpacingEm: 0.1,
  lineHeight: 1.6,
  lineWidthCh: 65,
  theme: 'warm-cream',
  aids: {
    bionicReading: false,
    readingRuler: false,
    bimodalHighlighting: true,
  },
};

export const OFFLINE_PRESETS: PresetSummary[] = [
  {
    displayName: 'Default Warm Cream',
    profile: DEFAULT_PROFILE,
  },
  {
    displayName: 'Bionic Focus',
    profile: {
      schemaVersion: 1,
      fontFamily: 'Lexend',
      fontSizePt: 20,
      letterSpacingEm: 0.02,
      wordSpacingEm: 0.1,
      lineHeight: 1.6,
      lineWidthCh: 65,
      theme: 'warm-cream',
      aids: {
        bionicReading: true,
        readingRuler: false,
        bimodalHighlighting: true,
      },
    },
  },
  {
    displayName: 'Large Print Audio',
    profile: {
      schemaVersion: 1,
      fontFamily: 'OpenDyslexic',
      fontSizePt: 32,
      letterSpacingEm: 0.05,
      wordSpacingEm: 0.2,
      lineHeight: 1.8,
      lineWidthCh: 50,
      theme: 'sepia',
      aids: {
        bionicReading: false,
        readingRuler: true,
        bimodalHighlighting: true,
      },
    },
  },
  {
    displayName: 'High Contrast',
    profile: {
      schemaVersion: 1,
      fontFamily: 'Atkinson Hyperlegible',
      fontSizePt: 20,
      letterSpacingEm: 0.04,
      wordSpacingEm: 0.1,
      lineHeight: 1.7,
      lineWidthCh: 65,
      theme: 'dark-high-contrast',
      aids: {
        bionicReading: false,
        readingRuler: false,
        bimodalHighlighting: true,
      },
    },
  },
];

function clampNumber(value: unknown, min: number, max: number, fallback: number): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) return fallback;
  return Math.min(max, Math.max(min, value));
}

export function toCanonicalProfile(raw: unknown): ReadingProfile {
  const input = (raw && typeof raw === 'object' ? raw : {}) as Record<string, unknown>;
  const aidsInput = (input.aids && typeof input.aids === 'object' ? input.aids : {}) as Record<string, unknown>;

  const fontFamily: FontFamily =
    input.fontFamily === 'Lexend' ||
    input.fontFamily === 'Atkinson Hyperlegible' ||
    input.fontFamily === 'OpenDyslexic'
      ? input.fontFamily
      : DEFAULT_PROFILE.fontFamily;

  const theme: ColorTheme =
    input.theme === 'warm-cream' ||
    input.theme === 'sepia' ||
    input.theme === 'dark-high-contrast'
      ? input.theme
      : DEFAULT_PROFILE.theme;

  return {
    schemaVersion: 1,
    fontFamily,
    fontSizePt: clampNumber(input.fontSizePt, 14, 48, DEFAULT_PROFILE.fontSizePt),
    letterSpacingEm: Number(clampNumber(input.letterSpacingEm, 0, 0.2, DEFAULT_PROFILE.letterSpacingEm).toFixed(3)),
    wordSpacingEm: Number(clampNumber(input.wordSpacingEm, 0, 0.5, DEFAULT_PROFILE.wordSpacingEm).toFixed(3)),
    lineHeight: Number(clampNumber(input.lineHeight, 1, 2.5, DEFAULT_PROFILE.lineHeight).toFixed(2)),
    lineWidthCh: Math.round(clampNumber(input.lineWidthCh, 35, 100, DEFAULT_PROFILE.lineWidthCh)),
    theme,
    aids: {
      bionicReading: typeof aidsInput.bionicReading === 'boolean' ? aidsInput.bionicReading : DEFAULT_PROFILE.aids.bionicReading,
      readingRuler: typeof aidsInput.readingRuler === 'boolean' ? aidsInput.readingRuler : DEFAULT_PROFILE.aids.readingRuler,
      bimodalHighlighting:
        typeof aidsInput.bimodalHighlighting === 'boolean'
          ? aidsInput.bimodalHighlighting
          : DEFAULT_PROFILE.aids.bimodalHighlighting,
    },
  };
}
