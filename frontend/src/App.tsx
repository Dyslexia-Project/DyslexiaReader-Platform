import { useState, useRef, type MouseEvent } from 'react';
import type { ReadingProfile, FontFamily } from './types/profile';
import { DEFAULT_PROFILE } from './types/profile';
import { ProfileEditor } from './components/ProfileEditor';
import { applyBionicReading, applySyllableSplit } from './utils/textTransform';
import { exportProfileJson } from './utils/exportProfile';

export default function App() {
  const [profile, setProfile] = useState<ReadingProfile>(DEFAULT_PROFILE);
  const [sampleText, setSampleText] = useState<string>(
    'Dyslexia is a neurodivergent condition that affects how the brain processes written text. Adjusting typographic controls, letter spacing, and line focus significantly improves readability and comprehension.'
  );

  // Audio & Karaoke TTS State
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [activeWordIndex, setActiveWordIndex] = useState<number | null>(null);
  const [statusMessage, setStatusMessage] = useState<string>('');

  // Mouse tracking state for the interactive reading ruler spotlight
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

  const getFontFamilyCSS = (font: FontFamily): string => {
    switch (font) {
      case 'OpenDyslexic': return '"OpenDyslexic", "OpenDyslexicRegular", sans-serif';
      case 'Lexend': return '"Lexend", sans-serif';
      case 'Atkinson Hyperlegible': return '"Atkinson Hyperlegible", sans-serif';
      case 'Sans-Serif':
      default: return 'system-ui, -apple-system, sans-serif';
    }
  };

  // Step 5: Cloud TTS Web Preview with Karaoke word highlighting
  const handleCloudTtsPreview = async () => {
    if (isPlaying) {
      window.speechSynthesis.cancel();
      setIsPlaying(false);
      setActiveWordIndex(null);
      return;
    }

    setStatusMessage('Synthesizing speech...');
    setIsPlaying(true);

    try {
      const response = await fetch('/api/v1/speech/synthesize', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: sampleText }),
      });

      if (!response.ok) {
        throw new Error('Backend TTS endpoint unreachable, falling back to Web Speech API');
      }
      setStatusMessage('Playing backend synthesized speech...');
    } catch {
      // Browser Speech API fallback for reliable demo presentation
      setStatusMessage('Playing TTS Preview (Browser Web Speech Engine)...');
      const utterance = new SpeechSynthesisUtterance(sampleText);
      utterance.rate = 0.9;

      const words = sampleText.split(' ');
      utterance.onboundary = (event) => {
        if (event.name === 'word') {
          const charIndex = event.charIndex;
          let currentLength = 0;
          for (let i = 0; i < words.length; i++) {
            currentLength += words[i].length + 1;
            if (currentLength > charIndex) {
              setActiveWordIndex(i);
              break;
            }
          }
        }
      };

      utterance.onend = () => {
        setIsPlaying(false);
        setActiveWordIndex(null);
        setStatusMessage('TTS playback completed.');
      };

      window.speechSynthesis.speak(utterance);
    }
  };

  // Step 6: Load Preset from Backend API (/api/v1/profiles/presets)
  const handleLoadPreset = async () => {
    setStatusMessage('Fetching preset from backend...');
    try {
      const res = await fetch('/api/v1/profiles/presets');
      if (!res.ok) throw new Error('Preset API unavailable');
      const data = await res.json();
      setProfile(data);
      setStatusMessage('Preset loaded successfully from backend!');
    } catch {
      // Fallback mock preset if Spring Boot API is not running locally
      const mockPreset: ReadingProfile = {
        fontFamily: 'OpenDyslexic',
        fontSize: 22,
        letterSpacing: 2.5,
        wordSpacing: 5,
        lineHeight: 2.0,
        theme: 'sepia',
        bionicReading: true,
        readingRuler: false,
        syllableSplit: false,
      };
      setProfile(mockPreset);
      setStatusMessage('Demo Preset loaded (OpenDyslexic Sepia Focus)!');
    }
  };

  // Step 7: Export Profile JSON
  const handleExportProfile = () => {
    exportProfileJson(profile);
    setStatusMessage('reading-profile.json exported successfully!');
  };

  // Renders sample text with Karaoke word highlighting and typography transformations
  const renderInteractiveText = () => {
    const words = sampleText.split(' ');

    return words.map((word, idx) => {
      let processedWord = word;
      if (profile.syllableSplit) processedWord = applySyllableSplit(processedWord);
      if (profile.bionicReading) processedWord = applyBionicReading(processedWord);

      const isHighlighted = activeWordIndex === idx;

      return (
        <span
          key={idx}
          style={{
            backgroundColor: isHighlighted ? '#ffeb3b' : 'transparent',
            color: isHighlighted ? '#000000' : 'inherit',
            padding: '2px 4px',
            borderRadius: '4px',
            transition: 'background-color 0.1s ease',
          }}
          dangerouslySetInnerHTML={{ __html: processedWord + ' ' }}
        />
      );
    });
  };

  return (
    <div style={{ padding: '24px', fontFamily: 'system-ui, sans-serif' }}>
      <h1>Dyslexia Reading Playground</h1>

      {/* Action Toolbar */}
      <div style={{ display: 'flex', gap: '12px', marginBottom: '20px', flexWrap: 'wrap' }}>
        <button
          onClick={handleLoadPreset}
          style={{ padding: '10px 16px', fontWeight: 'bold', cursor: 'pointer', backgroundColor: '#2196F3', color: '#fff', border: 'none', borderRadius: '6px' }}
        >
          📥 Load Preset from Backend
        </button>

        <button
          onClick={handleExportProfile}
          style={{ padding: '10px 16px', fontWeight: 'bold', cursor: 'pointer', backgroundColor: '#4CAF50', color: '#fff', border: 'none', borderRadius: '6px' }}
        >
          💾 Export Profile JSON
        </button>

        <button
          onClick={handleCloudTtsPreview}
          style={{ padding: '10px 16px', fontWeight: 'bold', cursor: 'pointer', backgroundColor: isPlaying ? '#f44336' : '#9C27B0', color: '#fff', border: 'none', borderRadius: '6px' }}
        >
          {isPlaying ? '⏹️ Stop TTS' : '🔊 Cloud TTS Web Preview'}
        </button>
      </div>

      {statusMessage && (
        <div style={{ padding: '10px', backgroundColor: '#e3f2fd', color: '#0d47a1', borderRadius: '6px', marginBottom: '16px', fontSize: '14px' }}>
          {statusMessage}
        </div>
      )}

      <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap' }}>
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

            <div>{renderInteractiveText()}</div>
          </div>
        </div>
      </div>
    </div>
  );
}