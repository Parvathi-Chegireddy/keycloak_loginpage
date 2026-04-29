import { useState, useEffect, useCallback, useRef } from 'react'
import LoginPage  from './LoginPage.jsx'
import MainLayout from './MainLayout.jsx'

/* ── callRefresh ─────────────────────────────────────────────────────────
   Returns parsed {accessToken, expiresIn} directly — NOT a Response.
   Singleton pattern: all concurrent callers share one request.        ── */
let _refreshPromise = null
function callRefresh() {
  if (_refreshPromise) return _refreshPromise
  _refreshPromise = fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
    .then(r => { if (!r.ok) throw new Error(`refresh_${r.status}`); return r.json() })
    .finally(() => { _refreshPromise = null })
  return _refreshPromise
}

const KEY = 'spantag_profile'
const saveProfile  = p  => { try { sessionStorage.setItem(KEY, JSON.stringify(p)) } catch (_) {} }
const loadProfile  = () => { try { return JSON.parse(sessionStorage.getItem(KEY) || 'null') } catch (_) { return null } }
const clearProfile = () => { try { sessionStorage.removeItem(KEY) } catch (_) {} }

export default function App() {
  const [profile,     setProfile]     = useState(null)
  const [loading,     setLoading]     = useState(true)
  const [gatewayDown, setGatewayDown] = useState(false)
  const [page,        setPage]        = useState('profile')

  const tokenRef        = useRef(null)   // access token — never causes re-renders
  const refreshTimerRef = useRef(null)
  const restoredRef     = useRef(false)

  const storeToken = useCallback((t) => { tokenRef.current = t }, [])

  /* ── authFetch ───────────────────────────────────────────────────────────
     Reads token from ref (no stale-closure risk).
     On 401: refreshes once, then retries with the fresh token.
  ── */
  const authFetch = useCallback(async (url, options = {}) => {
    const go = (tok) => {
      const h = { 'Content-Type': 'application/json', ...(options.headers || {}) }
      if (tok) h['Authorization'] = `Bearer ${tok}`
      return fetch(url, { ...options, headers: h, credentials: 'include' })
    }

    let res = await go(tokenRef.current)
    if (res.status === 401) {
      try {
        const data = await callRefresh()        // returns {accessToken, expiresIn}
        if (data?.accessToken) {
          storeToken(data.accessToken)
          res = await go(data.accessToken)      // retry with fresh token
        }
      } catch (_) { /* refresh failed — return original 401 */ }
    }
    return res
  }, [storeToken])

  /* ── Session restore on mount (runs once) ────────────────────────────── */
  useEffect(() => {
    if (restoredRef.current) return
    restoredRef.current = true

    ;(async () => {
      if (window.location.pathname === '/oauth2/callback') {
        const p = new URLSearchParams(window.location.search)
        const op = {
          username:    p.get('username')    || 'User',
          displayName: p.get('displayName') || p.get('username') || 'User',
          email:       p.get('email')       || '',   avatar:      p.get('avatar')      || '',
          role:        p.get('role')        || 'ROLE_USER',
          roleLabel:   p.get('roleLabel')   || 'USER',
          loginMethod: p.get('loginMethod') || 'oauth2',
          methodLabel: p.get('methodLabel') || 'OAuth2',
          provider:    p.get('provider')    || 'oauth2',
        }
        try {
          const r = await fetch('/api/profile/refresh', { method:'POST', credentials:'include' })
          if (r.ok) { const d = await r.json(); storeToken(d.accessToken) }
        } catch (_) {}
        window.history.replaceState({}, '', '/')
        saveProfile(op); setProfile(op); setLoading(false)
        return
      }

      try {
        const data = await callRefresh()
        storeToken(data.accessToken)
        const cached = loadProfile()
        if (cached) { setProfile(cached); setLoading(false); return }
        setProfile({
          username: decodeUsername(data.accessToken) || 'User',
          displayName: decodeUsername(data.accessToken) || 'User',
          email:'', avatar:'', role:'ROLE_USER', roleLabel:'USER',
          loginMethod:'keycloak', methodLabel:'Password Login', provider:'keycloak',
        })
      } catch (e) {
        if (e?.name === 'TypeError' || e?.message?.includes('Failed')) setGatewayDown(true)
      }
      setLoading(false)
    })()
  }, [storeToken])

  /* ── Auto-refresh every 14 min ───────────────────────────────────────── */
  const scheduleRefresh = useCallback(() => {
    clearTimeout(refreshTimerRef.current)
    refreshTimerRef.current = setTimeout(async () => {
      try { const d = await callRefresh(); storeToken(d.accessToken); scheduleRefresh() }
      catch (_) { doLogout() }
    }, 14 * 60 * 1000)
  }, [storeToken]) // eslint-disable-line

  useEffect(() => {
    if (profile) scheduleRefresh()
    return () => clearTimeout(refreshTimerRef.current)
  }, [profile, scheduleRefresh])

  const handleLoginSuccess = useCallback((data) => {
    storeToken(data.accessToken || null)
    const p = {
      username:    data.username    || 'User',
      displayName: data.displayName || data.username || 'User',
      email:       data.email       || '', avatar: data.avatar || '',
      role:        data.role        || 'ROLE_USER',
      roleLabel:   (data.role||'').replace('ROLE_','') || 'USER',
      loginMethod: data.loginMethod || 'keycloak',
      methodLabel: data.methodLabel || 'Password Login',
      provider:    data.provider    || 'keycloak',
    }
    setGatewayDown(false); saveProfile(p); setProfile(p); setPage('profile')
  }, [storeToken])

  const doLogout = useCallback(async () => {
    clearTimeout(refreshTimerRef.current)
    clearProfile(); storeToken(null)
    await fetch('/api/auth/logout', { method:'POST', credentials:'include' }).catch(()=>{})
    setProfile(null); setPage('profile')
  }, [storeToken])

  /* ── Render ──────────────────────────────────────────────────────────── */
  if (loading) return <Splash text="Restoring session…" />
  if (gatewayDown) return <GatewayDown />
  if (!profile)  return <LoginPage onLoginSuccess={handleLoginSuccess} />
  return (
    <MainLayout
      profile={profile}
      setProfile={p => { setProfile(p); if (p) saveProfile(p) }}
      authFetch={authFetch}
      page={page} setPage={setPage} onLogout={doLogout}
    />
  )
}

function Splash({ text }) {
  return (
    <div className="page">
      <div className="card" style={{ textAlign:'center', padding:'48px' }}>
        <span className="spinner" style={{ display:'inline-block' }} />
        <p className="footer-note" style={{ marginTop:'16px' }}>{text}</p>
      </div>
      <div className="bg-grid" aria-hidden="true" />
    </div>
  )
}

function GatewayDown() {
  return (
    <div className="page">
      <div className="card" style={{ textAlign:'center', padding:'48px' }}>
        <div style={{ fontSize:'2rem', marginBottom:'16px' }}>⚠</div>
        <h2 style={{ fontFamily:'var(--font-head)', fontSize:'1.2rem', marginBottom:'12px' }}>
          Gateway Unreachable
        </h2>
        <p style={{ fontSize:'.8rem', color:'var(--muted)', marginBottom:'24px' }}>
          Cannot connect to <code style={{ color:'var(--accent)' }}>localhost:1013</code>.
          Start all backend services first.
        </p>
        <button className="btn-primary" onClick={() => window.location.reload()}>↻ Retry</button>
      </div>
      <div className="bg-grid" aria-hidden="true" />
    </div>
  )
}

function decodeUsername(token) {
  try {
    const p = JSON.parse(atob(token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/')))
    return p.preferred_username || p.sub || null
  } catch (_) { return null }
}