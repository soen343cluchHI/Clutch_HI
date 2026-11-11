import { useRef, useState } from 'react';
import { presetAddresses, searchAddress, toKm } from '../services/geo';

// Search an address in Montreal, pick a suggestion, or tap the map (handled by the parent through onPick).
export default function AddressPicker({ origin, value, onChange, disabled }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [status, setStatus] = useState('');
  const pending = useRef(null);

  const choose = (place) => {
    onChange({ label: place.label, ...toKm(origin, place.latitude, place.longitude) });
    setResults([]);
    setQuery('');
    setStatus('');
  };

  async function search(event) {
    event.preventDefault();
    const text = query.trim();
    if (text.length < 3) {
      setStatus('Type at least 3 characters.');
      return;
    }
    pending.current?.abort();
    pending.current = new AbortController();
    setStatus('Searching Montreal…');
    try {
      const found = await searchAddress(text, pending.current.signal);
      setResults(found);
      setStatus(found.length ? '' : 'No Montreal address matches. Try a street and number, or tap the map.');
    } catch (failure) {
      if (failure.name !== 'AbortError') setStatus(failure.message);
    }
  }

  return (
    <div className="address-picker">
      {value ? (
        <div className="address-chosen">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 22s7-6.2 7-12a7 7 0 0 0-14 0c0 5.8 7 12 7 12Z" />
            <circle cx="12" cy="10" r="2.5" />
          </svg>
          <span>{value.label}</span>
          <button type="button" className="link-button" disabled={disabled} onClick={() => onChange(null)}>
            Change
          </button>
        </div>
      ) : (
        <>
          <form className="address-search" onSubmit={search}>
            <label className="visually-hidden" htmlFor="address-query">
              Delivery address
            </label>
            <input
              id="address-query"
              value={query}
              disabled={disabled}
              placeholder="Street address in Montreal"
              autoComplete="street-address"
              onChange={(e) => setQuery(e.target.value)}
            />
            <button className="f-btn soft sm" disabled={disabled}>
              Search
            </button>
          </form>
          {status && <p className="hint">{status}</p>}
          <ul className="address-results">
            {(results.length ? results : presetAddresses).map((place) => (
              <li key={`${place.label}-${place.latitude}`}>
                <button type="button" disabled={disabled} onClick={() => choose(place)}>
                  {place.label}
                </button>
              </li>
            ))}
          </ul>
          {!results.length && <p className="hint">Suggested places · or tap the map below</p>}
        </>
      )}
    </div>
  );
}
