import { useEffect, useRef, useState } from 'react';

// Output console: every command the user issues and every error, newest at the bottom.
export default function ConsoleDrawer({ logs, onClear }) {
  const [open, setOpen] = useState(false);
  const [errorsOnly, setErrorsOnly] = useState(false);
  const body = useRef(null);
  const errors = logs.filter((entry) => !entry.success).length;
  const shown = errorsOnly ? logs.filter((entry) => !entry.success) : logs;
  const last = logs[logs.length - 1];

  useEffect(() => {
    if (body.current) body.current.scrollTop = body.current.scrollHeight;
  }, [logs.length, open, errorsOnly]);

  return (
    <section className={`console-drawer${open ? ' console-open' : ''}`} aria-label="Command console">
      <header className="console-head">
        <button type="button" className="console-toggle" aria-expanded={open} onClick={() => setOpen(!open)}>
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <path d="m5 8 4 4-4 4M12 16h7" />
          </svg>
          Console
          <span className="count-badge">{logs.length}</span>
          {errors > 0 && <span className="console-errors">{errors} error{errors === 1 ? '' : 's'}</span>}
        </button>
        {!open && last && (
          <p className={`console-peek ${last.success ? 'ok' : 'err'}`}>
            {last.success ? 'OK' : 'ERROR'} {last.action}: {last.message}
          </p>
        )}
        {open && (
          <div className="console-tools">
            <button type="button" className={`chip-toggle${errorsOnly ? '' : ' on'}`} onClick={() => setErrorsOnly(false)}>
              All
            </button>
            <button type="button" className={`chip-toggle${errorsOnly ? ' on' : ''}`} onClick={() => setErrorsOnly(true)}>
              Errors
            </button>
            <button type="button" className="link-button" onClick={onClear} disabled={!logs.length}>
              Clear
            </button>
          </div>
        )}
        <button type="button" className="link-button console-collapse" onClick={() => setOpen(!open)}>
          {open ? 'Collapse ▾' : 'Expand ▴'}
        </button>
      </header>
      {open && (
        <div className="console-body" ref={body} role="log" aria-live="polite" aria-relevant="additions">
          {!shown.length && <p className="console-empty">{errorsOnly ? 'No errors so far.' : 'Waiting for commands…'}</p>}
          {shown.map((entry) => (
            <p key={entry.id} className="console-line">
              <span className="console-time">{entry.time}</span>
              <span className={entry.success ? 'ok' : 'err'}>{entry.success ? 'OK' : 'ERROR'}</span>
              <span className="console-action">{entry.action}</span>
              <span className="console-message">{entry.message}</span>
            </p>
          ))}
        </div>
      )}
    </section>
  );
}
