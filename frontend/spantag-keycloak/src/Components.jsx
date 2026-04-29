/* ── Shared UI components ─────────────────────────────────── */

export function PageHeader({ title, subtitle, action }) {
  return (
    <div className="page-header">
      <div>
        <h1 className="page-title">{title}</h1>
        {subtitle && <p className="page-subtitle">{subtitle}</p>}
      </div>
      {action && <div>{action}</div>}
    </div>
  )
}

export function DetailRow({ label, value, highlight }) {
  return (
    <div className="detail-row">
      <span className="detail-key">{label}</span>
      <span className={`detail-val ${highlight ? 'detail-val--green' : ''}`}>{value}</span>
    </div>
  )
}

export function StatusBadge({ text, color, style: extraStyle }) {
  const colors = {
    teal:   { bg: '#0a1f18', border: '#0f6e56', text: '#5DCAA5' },
    green:  { bg: '#0d1f08', border: '#3B6D11', text: '#97C459' },
    red:    { bg: '#1f0a0a', border: '#A32D2D', text: '#f87171' },
    amber:  { bg: '#1a1400', border: '#854F0B', text: '#EF9F27' },
    purple: { bg: '#100f20', border: '#534AB7', text: '#AFA9EC' },
    blue:   { bg: '#08101f', border: '#185FA5', text: '#85B7EB' },
    gray:   { bg: '#141414', border: '#2a2a2a', text: '#888780' },
  }
  const c = colors[color] || colors.gray
  return (
    <span style={{
      display:       'inline-block',
      padding:       '2px 8px',
      borderRadius:  '4px',
      fontSize:      '11px',
      fontFamily:    'var(--font-mono)',
      letterSpacing: '.06em',
      background:    c.bg,
      border:        `1px solid ${c.border}`,
      color:         c.text,
      ...extraStyle,
    }}>
      {text}
    </span>
  )
}

export function EmptyState({ message }) {
  return (
    <div className="content-card" style={{textAlign:'center',padding:'48px 24px'}}>
      <p className="muted">{message}</p>
    </div>
  )
}