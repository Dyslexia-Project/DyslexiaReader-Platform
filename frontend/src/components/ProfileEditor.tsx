import React from 'react';
import type { ReadingProfile, FontFamily, ColorTheme } from '../types/profile';

interface ProfileEditorProps {
  profile: ReadingProfile;
  onChange: (updated: ReadingProfile) => void;
}

export const ProfileEditor: React.FC<ProfileEditorProps> = ({ profile, onChange }) => {
  const updateField = <K extends keyof ReadingProfile>(key: K, value: ReadingProfile[K]) => {
    onChange({ ...profile, [key]: value });
  };

  return (
    <div style={{ padding: '20px', border: '1px solid #ccc', borderRadius: '8px', maxWidth: '380px', width: '100%', boxSizing: 'border-box' }}>
      <h2 style={{ marginTop: 0 }}>Reading Profile Editor</h2>

      {/* Font Family Selector */}
      <div style={{ marginBottom: '15px' }}>
        <label><b>Font Family:</b> </label>
        <select 
          value={profile.fontFamily} 
          onChange={(e) => updateField('fontFamily', e.target.value as FontFamily)}
          style={{ width: '100%', padding: '8px', marginTop: '4px' }}
        >
          <option value="Lexend">Lexend</option>
          <option value="OpenDyslexic">OpenDyslexic</option>
          <option value="Atkinson Hyperlegible">Atkinson Hyperlegible</option>
          <option value="Sans-Serif">Standard Sans-Serif</option>
        </select>
      </div>

      {/* Font Size Slider */}
      <div style={{ marginBottom: '15px' }}>
        <label><b>Font Size:</b> {profile.fontSize}px</label>
        <input 
          type="range" 
          min="12" 
          max="36" 
          value={profile.fontSize} 
          onChange={(e) => updateField('fontSize', Number(e.target.value))}
          style={{ width: '100%' }}
        />
      </div>

      {/* Letter Spacing Slider */}
      <div style={{ marginBottom: '15px' }}>
        <label><b>Letter Spacing:</b> {profile.letterSpacing}px</label>
        <input 
          type="range" 
          min="0" 
          max="8" 
          step="0.5"
          value={profile.letterSpacing} 
          onChange={(e) => updateField('letterSpacing', Number(e.target.value))}
          style={{ width: '100%' }}
        />
      </div>

      {/* Word Spacing Slider */}
      <div style={{ marginBottom: '15px' }}>
        <label><b>Word Spacing:</b> {profile.wordSpacing}px</label>
        <input 
          type="range" 
          min="0" 
          max="15" 
          step="1"
          value={profile.wordSpacing} 
          onChange={(e) => updateField('wordSpacing', Number(e.target.value))}
          style={{ width: '100%' }}
        />
      </div>

      {/* Line Height Slider */}
      <div style={{ marginBottom: '15px' }}>
        <label><b>Line Height:</b> {profile.lineHeight}</label>
        <input 
          type="range" 
          min="1" 
          max="3" 
          step="0.1"
          value={profile.lineHeight} 
          onChange={(e) => updateField('lineHeight', Number(e.target.value))}
          style={{ width: '100%' }}
        />
      </div>

      {/* Color Theme Selector */}
      <div style={{ marginBottom: '15px' }}>
        <label><b>Color Theme:</b> </label>
        <select 
          value={profile.theme} 
          onChange={(e) => updateField('theme', e.target.value as ColorTheme)}
          style={{ width: '100%', padding: '8px', marginTop: '4px' }}
        >
          <option value="light">Light (Default)</option>
          <option value="sepia">Sepia (Soft Reading)</option>
          <option value="dark">Dark</option>
          <option value="high-contrast">High Contrast</option>
        </select>
      </div>

      <hr style={{ margin: '20px 0' }} />
      <h3 style={{ marginTop: 0 }}>Interactive Reading Aids</h3>

      {/* Accessibility Toggles */}
      <div style={{ marginBottom: '10px' }}>
        <label style={{ cursor: 'pointer' }}>
          <input 
            type="checkbox" 
            checked={profile.bionicReading} 
            onChange={(e) => updateField('bionicReading', e.target.checked)} 
          />
          {' '}<b>Bionic Reading</b> (Bold word prefixes)
        </label>
      </div>

      <div style={{ marginBottom: '10px' }}>
        <label style={{ cursor: 'pointer' }}>
          <input 
            type="checkbox" 
            checked={profile.readingRuler} 
            onChange={(e) => updateField('readingRuler', e.target.checked)} 
          />
          {' '}<b>Reading Ruler</b> (Mouse spotlight tracking)
        </label>
      </div>

      <div style={{ marginBottom: '10px' }}>
        <label style={{ cursor: 'pointer' }}>
          <input 
            type="checkbox" 
            checked={profile.syllableSplit} 
            onChange={(e) => updateField('syllableSplit', e.target.checked)} 
          />
          {' '}<b>Syllable Splitting</b>
        </label>
      </div>
    </div>
  );
};