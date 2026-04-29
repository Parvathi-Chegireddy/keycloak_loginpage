import { useState, useEffect, useCallback, useRef } from 'react'
import { PageHeader, StatusBadge, EmptyState } from './Components.jsx'

export default function PaymentsPage({ authFetch }) {
  const [payments, setPayments] = useState([])
  const [loading,  setLoading]  = useState(true)
  const [selected, setSelected] = useState(null)
  const [filter,   setFilter]   = useState('ALL')
  const fetchRef = useRef(false)   // React 19 double-fire guard

  const loadPayments = useCallback(async () => {
    if (fetchRef.current) return   // second invocation → exit immediately
    fetchRef.current = true        // set SYNCHRONOUSLY before any await

    setLoading(true)
    try {
      const res = await authFetch('/api/payment/my')
      if (res.ok) setPayments(await res.json())
      else fetchRef.current = false
    } catch (_) { fetchRef.current = false }
    setLoading(false)
  }, [authFetch])

  useEffect(() => { loadPayments() }, [loadPayments])

  const refresh = useCallback(() => { fetchRef.current = false; loadPayments() }, [loadPayments])

  const totals = payments.reduce((a, p) => {
    if (p.status==='SUCCESS')   a.success   += Number(p.amount||0)
    if (p.status==='FAILED')    a.failed    += Number(p.amount||0)
    if (p.status==='CANCELLED') a.cancelled += Number(p.amount||0)
    return a
  }, { success:0, failed:0, cancelled:0 })

  const counts  = payments.reduce((a,p) => { a[p.status]=(a[p.status]||0)+1; return a }, {})
  const filters = ['ALL','SUCCESS','FAILED','CANCELLED']
  const visible = filter==='ALL' ? payments : payments.filter(p => p.status===filter)

  return (
    <div className="content-page">
      <PageHeader
        title="Payments"
        subtitle="Payment history from all your orders"
        action={<button className="btn-action" onClick={refresh}>↻ Refresh</button>}
      />

      {payments.length > 0 && (
        <>
          <div className="stats-row" style={{ marginBottom:'16px' }}>
            <StatCard label="Total Paid"    value={`₹${totals.success.toLocaleString('en-IN')}`}    color="teal" />
            <StatCard label="Failed Amount" value={`₹${totals.failed.toLocaleString('en-IN')}`}     color="red" />
            <StatCard label="Cancelled"     value={`₹${totals.cancelled.toLocaleString('en-IN')}`}  color="amber" />
            <StatCard label="Total"         value={payments.length}                                  color="gray" />
          </div>
          <div style={{ display:'flex', gap:'6px', marginBottom:'16px', flexWrap:'wrap' }}>
            {filters.map(f => (
              <button key={f} onClick={() => setFilter(f)} style={{
                padding:'5px 12px', borderRadius:'4px', fontSize:'.72rem',
                fontFamily:'var(--font-mono)', cursor:'pointer', letterSpacing:'.06em',
                border: filter===f ? '1px solid var(--accent)' : '1px solid var(--border)',
                background: filter===f ? 'rgba(200,240,76,.1)' : 'transparent',
                color: filter===f ? 'var(--accent)' : 'var(--muted)', transition:'all .15s',
              }}>
                {f} ({f==='ALL' ? payments.length : counts[f]||0})
              </button>
            ))}
          </div>
        </>
      )}

      {loading ? (
        <div className="content-card">
          {[1,2,3].map(i => <div key={i} className="skeleton-block" style={{ height:'36px', marginBottom:'8px' }} />)}
        </div>
      ) : payments.length === 0 ? (
        <EmptyState message="No payments yet. Place an order to see payments here." />
      ) : visible.length === 0 ? (
        <EmptyState message={`No ${filter} payments found.`} />
      ) : (
        <div className="table-card">
          <table className="data-table">
            <thead><tr>
              <th>Payment ID</th><th>Order</th><th>Amount</th>
              <th>Status</th><th>Message</th><th>Date</th><th></th>
            </tr></thead>
            <tbody>
              {visible.map((p,i) => (
                <tr key={p.paymentId||i}>
                  <td className="mono" style={{ fontSize:'11px' }}>
                    {p.paymentId ? <span title={p.paymentId} style={{ cursor:'help' }}>{p.paymentId.substring(0,14)}…</span> : '—'}
                  </td>
                  <td className="mono" style={{ color:'var(--muted)' }}>#{p.orderId}</td>
                  <td className="mono">₹{Number(p.amount).toLocaleString('en-IN')}</td>
                  <td><PayBadge status={p.status} /></td>
                  <td className="muted" style={{ maxWidth:'160px', overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap', fontSize:'.76rem' }}>
                    {p.message||'—'}
                  </td>
                  <td className="muted">{fmt(p.createdAt)}</td>
                  <td><button className="btn-action" onClick={() => setSelected(p)}>Details</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {selected && (
        <Drawer onClose={() => setSelected(null)}>
          <PayDetail payment={selected} onClose={() => setSelected(null)} />
        </Drawer>
      )}
    </div>
  )
}

function PayDetail({ payment, onClose }) {
  const ok = payment.status==='SUCCESS', fail = payment.status==='FAILED'
  const c = ok?['#0a1f18','#0f6e56','#5DCAA5']:fail?['#1f0a0a','#A32D2D','#f87171']:['#1a1400','#854F0B','#EF9F27']
  return (
    <div style={{ padding:'28px 24px' }}>
      <div style={{ display:'flex', justifyContent:'space-between', marginBottom:'20px' }}>
        <div>
          <h2 style={{ fontFamily:'var(--font-head)', fontSize:'1.1rem', fontWeight:700 }}>Payment Details</h2>
          <p className="muted" style={{ fontSize:'.74rem', marginTop:'2px' }}>For Order #{payment.orderId}</p>
        </div>
        <button className="btn-action" onClick={onClose}>✕</button>
      </div>
      <div style={{ background:c[0], border:`1px solid ${c[1]}`, borderRadius:'6px',
        padding:'14px 16px', marginBottom:'20px', display:'flex', alignItems:'center', gap:'12px' }}>
        <span style={{ fontSize:'20px' }}>{ok?'✓':fail?'✗':'↩'}</span>
        <div>
          <div style={{ fontSize:'.84rem', fontWeight:600, color:c[2] }}>
            {ok?'Payment Successful':fail?'Payment Failed':'Payment Cancelled'}
          </div>
          <div style={{ fontSize:'.74rem', color:'var(--muted)', marginTop:'2px' }}>{payment.message||'—'}</div>
        </div>
      </div>
      <div style={{ background:'var(--surface2)', border:'1px solid var(--border)', borderRadius:'6px',
        padding:'16px', marginBottom:'20px', textAlign:'center' }}>
        <div style={{ fontSize:'.7rem', color:'var(--muted)', marginBottom:'4px', textTransform:'uppercase', letterSpacing:'.1em' }}>Amount</div>
        <div style={{ fontFamily:'var(--font-head)', fontSize:'2rem', fontWeight:700, color:c[2] }}>
          ₹{Number(payment.amount).toLocaleString('en-IN')}
        </div>
      </div>
      <div className="details-grid">
        <DR label="Payment ID" value={payment.paymentId||'—'} mono small />
        <DR label="Order ID"   value={`#${payment.orderId}`} mono />
        <DR label="Status"     value={<PayBadge status={payment.status} />} />
        <DR label="Message"    value={payment.message||'—'} />
        <DR label="Date"       value={fmtFull(payment.createdAt)} />
      </div>
    </div>
  )
}

function Drawer({ children, onClose }) {
  return (
    <>
      <div onClick={onClose} style={{ position:'fixed', inset:0, background:'rgba(0,0,0,.5)', zIndex:40, backdropFilter:'blur(2px)' }} />
      <div style={{ position:'fixed', top:0, right:0, bottom:0, width:'420px', maxWidth:'100vw',
        background:'var(--surface)', borderLeft:'1px solid var(--border)', zIndex:50, overflowY:'auto',
        animation:'slideIn .2s ease' }}>
        {children}
      </div>
      <style>{`@keyframes slideIn{from{transform:translateX(100%)}to{transform:translateX(0)}}`}</style>
    </>
  )
}
function StatCard({ label, value, color }) {
  const c = { teal:['#0a1f18','#0f6e56','#5DCAA5'], red:['#1f0a0a','#A32D2D','#f87171'],
    amber:['#1a1400','#854F0B','#EF9F27'], gray:['#141414','#2a2a2a','#888780'] }[color]||['#141414','#2a2a2a','#888780']
  return <div className="stat-card" style={{ background:c[0], borderColor:c[1] }}>
    <span className="stat-value" style={{ color:c[2] }}>{value}</span>
    <span className="stat-label">{label}</span>
  </div>
}
function PayBadge({ status }) {
  const [c,l] = ({SUCCESS:['teal','Success'],FAILED:['red','Failed'],
    CANCELLED:['amber','Cancelled'],PENDING:['gray','Pending']})[status]||['gray',status]
  return <StatusBadge text={l} color={c} />
}
function DR({ label, value, mono, small }) {
  return <div className="detail-row">
    <span className="detail-key">{label}</span>
    <span style={{ fontFamily:mono?'var(--font-mono)':undefined, fontSize:small?'11px':'.8rem',
      color:'var(--text)', wordBreak:'break-all', textAlign:'right', maxWidth:'60%' }}>{value}</span>
  </div>
}
const fmt     = dt => dt ? new Date(dt).toLocaleString('en-IN',{day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit'}) : '—'
const fmtFull = dt => dt ? new Date(dt).toLocaleString('en-IN',{day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit',second:'2-digit'}) : '—'