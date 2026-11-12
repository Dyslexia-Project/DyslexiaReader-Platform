import React from 'react';
import type { ColorTheme, FontFamily, ReadingAids, ReadingProfile } from '../types/profile';
import { COLOR_THEME_PALETTES } from '../types/profile';

interface ProfileEditorProps {
  profile: ReadingProfile;
  syllableSplit: boolean;
  onSyllableSplitChange: (enabled: boolean) => void;
  onChange: (updated: ReadingProfile) => void;
}

export const ProfileEditor: React.FC<ProfileEditorProps> = ({
  profile,
  syllableSplit,
  onSyllableSplitChange,
  onChange,
}) => {
  const updateField = <K extends keyof ReadingProfile>(key: K, value: ReadingProfile[K]) => {
    onChange({ ...profile, [key]: value });
  };

  const updateAid = <K extends keyof ReadingAids>(key: K, value: boolean) => {
    onChange({
      ...profile,
      aids: {
        ...profile.aids,
        [key]: value,
      },
    });
  };

  return (
    <div style={{ padding: '20px', border: '1px solid #ccc', borderRadius: '8px', maxWidth: '380px', width: '100%', boxSizing: 'border-box', textAlign: 'left' }}>
      <h2 style={{ marginTop: 0 }}>Reading Profile Editor</h2>

      <div style={{ marginBottom: '15px' }}>
        <label htmlFor="font-family-select"><b>Font Family:</b> </label>
        <select
          id="font-family-select"
          value={profile.fontFamily}
          onChange={(e) => updateField('fontFamily', e.target.value as FontFamily)}
          style={{ width: '100%', padding: '8px', marginTop: '4px' }}
        >
          <option value="Lexend">Lexend</option>
          <option value="OpenDyslexic">OpenDyslexic</option>
          <option value="Atkinson Hyperlegible">Atkinson Hyperlegible</option>
        </select>
      </div>

      <div style={{ marginBottom: '15px' }}>
        <label htmlFor="font-size-range"><b>Font Size:</b> {profile.fontSizePt}pt</label>
        <input
          id="font-size-range"
          type="range"
          min="14"
          max="48"
          step="1"
          value={profile.fontSizePt}
          onChange={(e) => updateField('fontSizePt', Number(e.target.value))}
          style={{ width: '100%' }}
        />
      </div>

      <div style={{ marginBottom: '15px' }}>
        <label htmlFor="letter-spacing-range"><b>Letter Spacing:</b> {profile.letterSpacingEm.toFixed(2)}em</label>
        <input
          id="letter-spacing-range"
          type="range"
          min="0"
          max="0.2"
          step="0.01"
          value={profile.letterSpacingEm}
          onChange={(e) => updateField('letterSpacingEm', Number(e.target.value))}
          style={{ width: '100%' }}
        />
      </div>

      <div style={{ marginBottom: '15px' }}>
        <label htmlFor="word-spacing-range"><b>Word Spacing:</b> {profile.wordSpacingEm.toFixed(2)}em</label>
        <input
          id="word-spacing-range"
          type="range"
          min="0"
          max="0.5"
          step="0.02"
          value={profile.wordSpacingEm}
          onChange={(e) => updateField('wordSpacingEm', Number(e.target.value))}
          style={{ width: '100%' }}
        />
      </div>

      <div style={{ marginBottom: '15px' }}>
        <label htmlFor="line-height-range"><b>Line Height:</b> {profile.lineHeight.toFixed(1)}</label>
        <input
          id="line-height-range"
          type="range"
          min="1"
          max="2.5"
          step="0.1"
          value={profile.lineHeight}
          onChange={(e) => updateField('lineHeight', Number(e.target.value))}
          style={{ width: '100%' }}
        />
      </div>

      <div style={{ marginBottom: '15px' }}>
        <label htmlFor="line-width-range"><b>Line Width:</b> {profile.lineWidthCh}ch</label>
        <input
          id="line-width-range"
          type="range"
          min="35"
          max="100"
          step="1"
          value={profile.lineWidthCh}
          onChange={(e) => updateField('lineWidthCh', Number(e.target.value))}
          style={{ width: '100%' }}
        />
      </div>

      <div style={{ marginBottom: '15px' }}>
        <label htmlFor="theme-select"><b>Color Theme:</b> </label>
        <select
          id="theme-select"
          value={profile.theme}
          onChange={(e) => updateField('theme', e.target.value as ColorTheme)}
          style={{ width: '100%', padding: '8px', marginTop: '4px' }}
        >
          {(Object.keys(COLOR_THEME_PALETTES) as ColorTheme[]).map((themeKey) => (
            <option key={themeKey} value={themeKey}>
              {COLOR_THEME_PALETTES[themeKey].label}
            </option>
          ))}
        </select>
      </div>

      <hr style={{ margin: '20px 0' }} />
      <h3 style={{ marginTop: 0 }}>Interactive Reading Aids</h3>

      <div style={{ marginBottom: '10px' }}>
        <label style={{ cursor: 'pointer' }}>
          <input
            type="checkbox"
            checked={profile.aids.bionicReading}
            onChange={(e) => updateAid('bionicReading', e.target.checked)}
          />
          {' '}<b>Bionic Reading</b> (Bold word prefixes)
        </label>
      </div>

      <div style={{ marginBottom: '10px' }}>
        <label style={{ cursor: 'pointer' }}>
          <input
            type="checkbox"
            checked={profile.aids.readingRuler}
            onChange={(e) => updateAid('readingRuler', e.target.checked)}
          />
          {' '}<b>Reading Ruler</b> (Mouse spotlight tracking)
        </label>
      </div>

      <div style={{ marginBottom: '10px' }}>
        <label style={{ cursor: 'pointer' }}>
          <input
            type="checkbox"
            checked={profile.aids.bimodalHighlighting}
            onChange={(e) => updateAid('bimodalHighlighting', e.target.checked)}
          />
          {' '}<b>Bimodal Highlighting</b> (Word sync during speech)
        </label>
      </div>

      <div style={{ marginBottom: '10px' }}>
        <label style={{ cursor: 'pointer' }}>
          <input
            type="checkbox"
            checked={syllableSplit}
            onChange={(e) => onSyllableSplitChange(e.target.checked)}
          />
          {' '}<b>Syllable Splitting</b> (Web preview only)
        </label>
      </div>
    </div>
  );
};
