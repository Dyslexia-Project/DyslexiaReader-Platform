import { useState, useRef, type MouseEvent } from 'react';
import type { ReadingProfile, FontFamily } from './types/profile';
import { DEFAULT_PROFILE } from './types/profile';
import { ProfileEditor } from './components/ProfileEditor';
import { applyBionicReading, applySyllableSplit } from './utils/textTransform';

export default function App() {
  const [profile, setProfile] = useState<ReadingProfile>(DEFAULT_PROFILE);
  const [sampleText, setSampleText] = useState<string>(
    'Dyslexia is a neurodivergent condition that affects how the brain processes written text. Adjusting typographic controls, letter spacing, and line focus significantly improves readability and comprehension.'
  );

  const [rulerY, setRulerY] = useState<number>(0);
  const previewRef = useRef<HTMLDivElement>(null);

  const handleMouseMove = (e: MouseEvent<HTMLDivElement>) => {
    if (!profile.readingRuler || !previewRef.current) return;
    const rect = previewRef.current.getBoundingClientRect();
    setRulerY(e.clientY - rect.top);
  };

  const getThemeStyles = () => {
    switch (profile.theme) {
      case 'sepia': return { backgroundColor: '#f4ecd8', color: '#5b4636' };
      case 'dark': return { backgroundColor: '#1e1e1e', color: '#f0f0f0' };
      case 'high-contrast': return { backgroundColor: '#000000', color: '#ffff00' };
      case 'light':
      default: return { backgroundColor: '#ffffff', color: '#111111' };
    }
  };

  // Maps FontFamily enum values to exact CSS font family declarations
  const getFontFamilyCSS = (font: FontFamily): string => {
    switch (font) {
      case 'OpenDyslexic':
        return '"OpenDyslexic", "OpenDyslexicRegular", sans-serif';
      case 'Lexend':
        return '"Lexend", sans-serif';
      case 'Atkinson Hyperlegible':
        return '"Atkinson Hyperlegible", sans-serif';
      case 'Sans-Serif':
      default:
        return 'system-ui, -apple-system, sans-serif';
    }
  };

  const getProcessedText = () => {
    let text = sampleText;
    if (profile.syllableSplit) {
      text = applySyllableSplit(text);
    }
    if (profile.bionicReading) {
      return applyBionicReading(text);
    }
    return text;
  };

  return (
    <div style={{ padding: '24px', fontFamily: 'system-ui, sans-serif' }}>
      <h1>Dyslexia Reading Playground</h1>

      <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap', marginTop: '20px' }}>
        <ProfileEditor profile={profile} onChange={setProfile} />

        <div style={{ flex: 1, minWidth: '320px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div>
            <label><b>Sample Text (Edit or paste custom text):</b></label>
            <textarea
              value={sampleText}
              onChange={(e) => setSampleText(e.target.value)}
              rows={3}
              style={{ width: '100%', padding: '10px', marginTop: '8px', fontSize: '14px', borderRadius: '6px', border: '1px solid #ccc', boxSizing: 'border-box' }}
            />
          </div>

          <div
            ref={previewRef}
            onMouseMove={handleMouseMove}
            style={{
              position: 'relative',
              padding: '30px',
              borderRadius: '8px',
              border: '2px dashed #bbb',
              overflow: 'hidden',
              minHeight: '250px',
              ...getThemeStyles(),
              fontFamily: getFontFamilyCSS(profile.fontFamily),
              fontSize: `${profile.fontSize}px`,
              letterSpacing: `${profile.letterSpacing}px`,
              wordSpacing: `${profile.wordSpacing}px`,
              lineHeight: profile.lineHeight,
            }}
          >
            {profile.readingRuler && (
              <div
                style={{
                  position: 'absolute',
                  left: 0,
                  right: 0,
                  top: `${rulerY - 20}px`,
                  height: '40px',
                  backgroundColor: 'rgba(255, 235, 59, 0.35)',
                  borderTop: '2px solid rgba(255, 193, 7, 0.8)',
                  borderBottom: '2px solid rgba(255, 193, 7, 0.8)',
                  pointerEvents: 'none',
                  transition: 'top 0.05s ease-out',
                }}
              />
            )}

            <div dangerouslySetInnerHTML={{ __html: getProcessedText() }} />
          </div>
        </div>
      </div>
    </div>
  );
}