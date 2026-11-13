import { useEffect, useRef, useState, type MouseEvent } from 'react';
import { ProfileEditor } from './components/ProfileEditor';
import type { FontFamily, PresetSummary, ReadingProfile } from './types/profile';
import {
  COLOR_THEME_PALETTES,
  DEFAULT_PROFILE,
  OFFLINE_PRESETS,
  toCanonicalProfile,
} from './types/profile';
import { exportProfileJson } from './utils/exportProfile';
import { applyBionicReading, applySyllableSplit } from './utils/textTransform';

interface SpeechMark {
  time: number;
  duration: number;
  type: 'sentence' | 'word';
  start: number;
  end: number;
  value: string;
}

interface WordToken {
  word: string;
  start: number;
  end: number;
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function tokenizeWords(text: string): WordToken[] {
  const tokens: WordToken[] = [];
  const regex = /\S+/g;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(text)) !== null) {
    tokens.push({
      word: match[0],
      start: match.index,
      end: match.index + match[0].length,
    });
  }
  return tokens;
}

export default function App() {
  const [profile, setProfile] = useState<ReadingProfile>(DEFAULT_PROFILE);
  const [syllableSplit, setSyllableSplit] = useState<boolean>(false);
  const [presets, setPresets] = useState<PresetSummary[]>(OFFLINE_PRESETS);
  const [selectedPresetName, setSelectedPresetName] = useState<string>(
    OFFLINE_PRESETS[0].displayName,
  );
  const [shareCodeInput, setShareCodeInput] = useState<string>('');
  const [generatedShareCode, setGeneratedShareCode] = useState<string>('');
  const [sampleText, setSampleText] = useState<string>(
    'Dyslexia is a neurodivergent condition that affects how the brain processes written text. Adjusting typographic controls, letter spacing, and line focus significantly improves readability and comprehension. When line width is adjusted between 45 and 65 characters, it reduces visual crowding and prevents readers from losing their place across lines.',
  );

  // Audio & Karaoke TTS State
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [activeWordIndex, setActiveWordIndex] = useState<number | null>(null);
  const [statusMessage, setStatusMessage] = useState<string>('');

  // Mouse tracking state for the interactive reading ruler spotlight
  const [rulerY, setRulerY] = useState<number>(0);
  const previewRef = useRef<HTMLDivElement>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const markTimersRef = useRef<number[]>([]);

  const clearPlayback = () => {
    window.speechSynthesis.cancel();
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current.src = '';
      audioRef.current = null;
    }
    for (const id of markTimersRef.current) {
      window.clearTimeout(id);
    }
    markTimersRef.current = [];
    setIsPlaying(false);
    setActiveWordIndex(null);
  };

  useEffect(() => {
    let active = true;
    const loadPresetsOnMount = async () => {
      try {
        const res = await fetch('/api/v1/profiles/presets');
        if (!res.ok) return;
        const data = (await res.json()) as Array<{ displayName?: unknown; profile?: unknown }>;
        if (!active || !Array.isArray(data) || data.length === 0) return;
        const normalized: PresetSummary[] = data
          .filter((item) => typeof item?.displayName === 'string')
          .map((item) => ({
            displayName: item.displayName as string,
            profile: toCanonicalProfile(item.profile),
          }));
        if (normalized.length > 0) {
          setPresets(normalized);
          setSelectedPresetName(normalized[0].displayName);
        }
      } catch {
        // Offline fallback keeps built-in presets available.
      }
    };
    void loadPresetsOnMount();
    return () => {
      active = false;
      clearPlayback();
    };
  }, []);

  const handleMouseMove = (e: MouseEvent<HTMLDivElement>) => {
    if (!profile.aids.readingRuler || !previewRef.current) return;
    const rect = previewRef.current.getBoundingClientRect();
    setRulerY(e.clientY - rect.top);
  };

  const themePalette = COLOR_THEME_PALETTES[profile.theme];

  const getFontFamilyCSS = (font: FontFamily): string => {
    switch (font) {
      case 'OpenDyslexic':
        return '"OpenDyslexic", "OpenDyslexicRegular", sans-serif';
      case 'Lexend':
        return '"Lexend", sans-serif';
      case 'Atkinson Hyperlegible':
      default:
        return '"Atkinson Hyperlegible", sans-serif';
    }
  };

  const handleCloudTtsPreview = async () => {
    if (isPlaying) {
      clearPlayback();
      return;
    }

    setStatusMessage('Synthesizing speech...');
    setIsPlaying(true);

    const tokens = tokenizeWords(sampleText);

    try {
      const response = await fetch('/api/v1/speech/synthesize', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          text: sampleText,
          voiceId: 'en-US-Neural2-F',
          speed: 1.0,
          language: 'en-US',
        }),
      });

      if (!response.ok) {
        throw new Error('Backend TTS endpoint unavailable');
      }

      const data = (await response.json()) as {
        audioMimeType?: string;
        audioBase64?: string;
        speechMarks?: SpeechMark[];
        cacheHit?: boolean;
      };

      if (!data.audioBase64) {
        throw new Error('Backend response missing audio payload');
      }

      const mime = data.audioMimeType || 'audio/mpeg';
      const audio = new Audio(`data:${mime};base64,${data.audioBase64}`);
      audioRef.current = audio;

      const wordMarks = Array.isArray(data.speechMarks)
        ? data.speechMarks.filter((mark) => mark.type === 'word')
        : [];

      const scheduleHighlights = () => {
        if (!profile.aids.bimodalHighlighting) return;
        for (const mark of wordMarks) {
          const tokenIndex = tokens.findIndex(
            (token) => mark.start >= token.start && mark.start < token.end,
          );
          if (tokenIndex >= 0) {
            const timerId = window.setTimeout(() => {
              setActiveWordIndex(tokenIndex);
            }, Math.max(0, mark.time));
            markTimersRef.current.push(timerId);
          }
        }
      };

      audio.onended = () => {
        clearPlayback();
        setStatusMessage('Cloud TTS playback completed.');
      };
      audio.onerror = () => {
        clearPlayback();
        setStatusMessage('Cloud audio could not play; check browser audio settings.');
      };

      await audio.play();
      scheduleHighlights();
      setStatusMessage(
        data.cacheHit
          ? 'Playing Cloud TTS (served from platform cache).'
          : 'Playing Cloud TTS from Spring Boot backend.',
      );
    } catch {
      // Browser Speech API fallback when backend or Google TTS API key is unavailable
      setStatusMessage('Playing TTS Preview (Browser Web Speech fallback)...');
      const utterance = new SpeechSynthesisUtterance(sampleText);
      utterance.rate = 0.95;

      utterance.onboundary = (event) => {
        if (!profile.aids.bimodalHighlighting || event.name !== 'word') return;
        const idx = tokens.findIndex(
          (token) => event.charIndex >= token.start && event.charIndex < token.end,
        );
        if (idx >= 0) {
          setActiveWordIndex(idx);
        }
      };

      utterance.onend = () => {
        clearPlayback();
        setStatusMessage('TTS playback completed.');
      };

      window.speechSynthesis.speak(utterance);
    }
  };

  const handleLoadPreset = async () => {
    setStatusMessage(`Loading preset "${selectedPresetName}"...`);
    try {
      const res = await fetch('/api/v1/profiles/presets');
      if (!res.ok) throw new Error('Preset API unavailable');
      const data = (await res.json()) as Array<{ displayName?: unknown; profile?: unknown }>;
      if (!Array.isArray(data) || data.length === 0) {
        throw new Error('No presets returned');
      }
      const normalized: PresetSummary[] = data
        .filter((item) => typeof item?.displayName === 'string')
        .map((item) => ({
          displayName: item.displayName as string,
          profile: toCanonicalProfile(item.profile),
        }));
      setPresets(normalized);
      const chosen =
        normalized.find((item) => item.displayName === selectedPresetName) ?? normalized[0];
      setSelectedPresetName(chosen.displayName);
      setProfile(chosen.profile);
      setStatusMessage(`Loaded preset "${chosen.displayName}" from Spring Boot backend.`);
    } catch {
      const fallback =
        presets.find((item) => item.displayName === selectedPresetName) ?? OFFLINE_PRESETS[0];
      setProfile(fallback.profile);
      setStatusMessage(`Backend unreachable; applied local preset "${fallback.displayName}".`);
    }
  };

  const handleShareToDesktop = async () => {
    setStatusMessage('Sharing profile to Platform for Desktop sync...');
    try {
      const canonical = toCanonicalProfile(profile);
      const res = await fetch('/api/v1/profiles/share', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(canonical),
      });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      const data = (await res.json()) as { code?: string };
      if (!data.code) {
        throw new Error('Response missing share code');
      }
      setGeneratedShareCode(data.code);
      setShareCodeInput(data.code);
      if (navigator.clipboard?.writeText) {
        void navigator.clipboard.writeText(data.code).catch(() => {});
      }
      setStatusMessage(
        `Share code ${data.code} created & copied! Enter it in Desktop -> Platform -> Import Share Code.`,
      );
    } catch (err) {
      const reason = err instanceof Error ? err.message : 'Backend unavailable';
      setStatusMessage(
        `Could not generate share code (${reason}). You can still Export Profile JSON for Desktop.`,
      );
    }
  };

  const handleImportShareCode = async () => {
    const code = shareCodeInput.trim().toUpperCase();
    if (!code) {
      setStatusMessage('Enter an 8-character share code first.');
      return;
    }
    setStatusMessage(`Fetching shared profile ${code}...`);
    try {
      const res = await fetch(`/api/v1/profiles/share/${encodeURIComponent(code)}`);
      if (!res.ok) {
        throw new Error(res.status === 404 ? 'Share code not found' : `HTTP ${res.status}`);
      }
      const raw = await res.json();
      setProfile(toCanonicalProfile(raw));
      setStatusMessage(`Loaded shared profile ${code} from Platform!`);
    } catch (err) {
      const reason = err instanceof Error ? err.message : 'Request failed';
      setStatusMessage(`Failed to load share code ${code}: ${reason}`);
    }
  };

  const handleExportProfile = () => {
    exportProfileJson(profile);
    setStatusMessage('Schema-v1 reading-profile.json exported (compatible with Desktop & Platform API)!');
  };

  const renderInteractiveText = () => {
    const tokens = tokenizeWords(sampleText);

    return tokens.map((token, idx) => {
      let processedWord = escapeHtml(token.word);
      if (syllableSplit) processedWord = applySyllableSplit(processedWord);
      if (profile.aids.bionicReading) processedWord = applyBionicReading(processedWord);

      const isHighlighted = profile.aids.bimodalHighlighting && activeWordIndex === idx;

      return (
        <span key={`${idx}-${token.start}`}>
          <span
            style={{
              backgroundColor: isHighlighted ? themePalette.sentenceHighlight : 'transparent',
              outline: isHighlighted ? `2px solid ${themePalette.wordHighlightBorder}` : 'none',
              padding: '1px 3px',
              borderRadius: '4px',
              transition: 'background-color 0.1s ease',
            }}
            dangerouslySetInnerHTML={{ __html: processedWord }}
          />{' '}
        </span>
      );
    });
  };

  return (
    <div style={{ padding: '24px', fontFamily: 'system-ui, sans-serif', textAlign: 'left' }}>
      <h1 style={{ marginTop: 0 }}>Dyslexia Reading Playground</h1>

      {/* Primary Action Toolbar */}
      <div style={{ display: 'flex', gap: '10px', marginBottom: '12px', flexWrap: 'wrap', alignItems: 'center' }}>
        <select
          aria-label="Choose Preset"
          value={selectedPresetName}
          onChange={(e) => setSelectedPresetName(e.target.value)}
          style={{ padding: '9px 10px', borderRadius: '6px', border: '1px solid #bbb' }}
        >
          {presets.map((preset) => (
            <option key={preset.displayName} value={preset.displayName}>
              {preset.displayName}
            </option>
          ))}
        </select>

        <button
          onClick={handleLoadPreset}
          style={{ padding: '10px 14px', fontWeight: 'bold', cursor: 'pointer', backgroundColor: '#155EEF', color: '#fff', border: 'none', borderRadius: '6px' }}
        >
          Load Preset
        </button>

        <button
          onClick={handleShareToDesktop}
          style={{ padding: '10px 14px', fontWeight: 'bold', cursor: 'pointer', backgroundColor: '#0E9F6E', color: '#fff', border: 'none', borderRadius: '6px' }}
        >
          Share to Desktop (Get Code)
        </button>

        <input
          type="text"
          value={shareCodeInput}
          onChange={(e) => setShareCodeInput(e.target.value.toUpperCase())}
          placeholder="Share code (e.g. K7P9M2WX)"
          aria-label="Share Code"
          maxLength={12}
          style={{ padding: '9px 10px', borderRadius: '6px', border: '1px solid #bbb', width: '190px', textTransform: 'uppercase' }}
        />

        <button
          onClick={handleImportShareCode}
          style={{ padding: '10px 14px', fontWeight: 'bold', cursor: 'pointer', backgroundColor: '#374151', color: '#fff', border: 'none', borderRadius: '6px' }}
        >
          Load Code
        </button>

        <button
          onClick={handleExportProfile}
          style={{ padding: '10px 14px', fontWeight: 'bold', cursor: 'pointer', backgroundColor: '#4CAF50', color: '#fff', border: 'none', borderRadius: '6px' }}
        >
          Export Profile JSON
        </button>

        <button
          onClick={handleCloudTtsPreview}
          style={{ padding: '10px 14px', fontWeight: 'bold', cursor: 'pointer', backgroundColor: isPlaying ? '#DC2626' : '#7E22CE', color: '#fff', border: 'none', borderRadius: '6px' }}
        >
          {isPlaying ? 'Stop TTS' : 'Cloud TTS Preview'}
        </button>
      </div>

      {generatedShareCode && (
        <div style={{ padding: '10px 14px', backgroundColor: '#ECFDF5', color: '#065F46', border: '1px solid #A7F3D0', borderRadius: '6px', marginBottom: '12px', fontSize: '14px' }}>
          <b>Desktop Sync Code:</b> <code style={{ fontSize: '16px', fontWeight: 'bold' }}>{generatedShareCode}</code> — paste this code into <b>Dyslexia Reader Desktop → Platform → Import Share Code</b>.
        </div>
      )}

      {statusMessage && (
        <div style={{ padding: '10px 14px', backgroundColor: '#EFF6FF', color: '#1E3A8A', border: '1px solid #BFDBFE', borderRadius: '6px', marginBottom: '16px', fontSize: '14px' }}>
          {statusMessage}
        </div>
      )}

      <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <ProfileEditor
          profile={profile}
          syllableSplit={syllableSplit}
          onSyllableSplitChange={setSyllableSplit}
          onChange={setProfile}
        />

        <div style={{ flex: 1, minWidth: '320px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div>
            <label htmlFor="sample-text-input"><b>Sample Text (Edit or paste custom text):</b></label>
            <textarea
              id="sample-text-input"
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
              border: `2px solid ${themePalette.border}`,
              overflowX: 'auto',
              overflowY: 'hidden',
              minHeight: '250px',
              backgroundColor: themePalette.background,
              color: themePalette.text,
              fontFamily: getFontFamilyCSS(profile.fontFamily),
              fontSize: `${profile.fontSizePt}pt`,
              letterSpacing: `${profile.letterSpacingEm}em`,
              wordSpacing: `${profile.wordSpacingEm}em`,
              lineHeight: profile.lineHeight,
            }}
          >
            {profile.aids.readingRuler && (
              <div
                style={{
                  position: 'absolute',
                  left: 0,
                  right: 0,
                  top: `${rulerY - 20}px`,
                  height: '40px',
                  backgroundColor: `${themePalette.rulerHighlight}88`,
                  borderTop: `2px solid ${themePalette.focus}`,
                  borderBottom: `2px solid ${themePalette.focus}`,
                  pointerEvents: 'none',
                  transition: 'top 0.05s ease-out',
                }}
              />
            )}

            <div
              style={{
                width: `${profile.lineWidthCh}ch`,
                maxWidth: '100%',
                position: 'relative',
                borderRight: `2px dashed ${themePalette.focus}88`,
                paddingRight: '12px',
                boxSizing: 'border-box',
                transition: 'width 0.15s ease-out',
              }}
              title={`Line Width: ${profile.lineWidthCh}ch`}
            >
              <div style={{ fontSize: '11px', opacity: 0.6, marginBottom: '6px', userSelect: 'none' }}>
                Column Width: {profile.lineWidthCh}ch (dashed line marks margin)
              </div>
              {renderInteractiveText()}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
