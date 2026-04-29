import { useState } from 'react'

const GATEWAY = 'http://localhost:1013'

export default function LoginPage({ onLoginSuccess }) {
  const [tab, setTab] = useState('login')
  return (
    <div className="page">
      <div className="card">
        <div className="card-header">
          <div className="logo-mark">⬡</div>
          <h1 className="title">{tab === 'register' ? 'REGISTER' : 'SIGN IN'}</h1>
          <p className="subtitle">
            {tab === 'login'    && 'Welcome back'}
            {tab === 'register' && 'Create your account'}
            {tab === 'oauth2'   && 'Continue with a provider'}
          </p>
        </div>

        <div className="tabs tabs--three">
          <button className={`tab ${tab==='login'    ? 'tab--active':''}`} onClick={()=>setTab('login')}>Login</button>
          <button className={`tab ${tab==='register' ? 'tab--active':''}`} onClick={()=>setTab('register')}>Register</button>
          <button className={`tab ${tab==='oauth2'   ? 'tab--active':''}`} onClick={()=>setTab('oauth2')}>OAuth2</button>
          <span className="tab-indicator tab-indicator--three" style={{
            left: tab==='login' ? '4px' : tab==='register' ? 'calc(33.33%)' : 'calc(66.66%)'
          }}/>
        </div>

        {tab === 'login'    && <LoginForm    onLoginSuccess={onLoginSuccess} />}
        {tab === 'register' && <RegisterForm onSwitchToLogin={() => setTab('login')} />}
        {tab === 'oauth2'   && <OAuth2Panel  gateway={GATEWAY} />}

        <p className="footer-note">
          Production JWT · HttpOnly cookies · WebClient · Gateway :1013
        </p>
      </div>
      <div className="bg-grid" aria-hidden="true"/>
    </div>
  )
}

/* ── Login Form ─────────────────────────────────────────── */
function LoginForm({ onLoginSuccess }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState('')

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    if (!username || !password) { setError('Please fill in both fields.'); return }
    setLoading(true)
    try {
      const res = await fetch('/api/auth/login', {
        method:      'POST',
        headers:     { 'Content-Type': 'application/json' },
        credentials: 'include',
        body:        JSON.stringify({ username: username.trim(), password }),
      })
      const data = await res.json().catch(() => ({}))
      if (res.ok && data.accessToken) {
        onLoginSuccess(data)
      } else {
        setError(data.message || `Login failed (HTTP ${res.status})`)
      }
    } catch (err) {
      setError(`Network error: ${err.message}`)
    } finally { setLoading(false) }
  }

  return (
    <form onSubmit={handleSubmit} className="form" noValidate>
      {error && <div className="error-box">{error}</div>}
      <div className="field">
        <label className="label">USERNAME</label>
        <input className="input" type="text" placeholder="your_username"
          value={username} onChange={e=>setUsername(e.target.value)} autoComplete="username"/>
      </div>
      <div className="field">
        <label className="label">PASSWORD</label>
        <input className="input" type="password" placeholder="••••••••"
          value={password} onChange={e=>setPassword(e.target.value)} autoComplete="current-password"/>
      </div>
      <button className="btn-primary" type="submit" disabled={loading}>
        {loading ? <span className="spinner"/> : 'SIGN IN'}
      </button>
    </form>
  )
}

/* ── Register Form ──────────────────────────────────────── */
function RegisterForm({ onSwitchToLogin }) {
  const [form, setForm] = useState({username:'',email:'',password:'',confirm:'',role:'ROLE_USER'})
  const [loading, setLoading] = useState(false)
  const [error,   setError]   = useState('')
  const [success, setSuccess] = useState('')
  const set = f => e => setForm(p => ({...p, [f]: e.target.value}))

  const validate = () => {
    if (!form.username.trim())  return 'Username is required.'
    if (form.username.length<3) return 'Username must be at least 3 characters.'
    if (!form.email.trim())     return 'Email is required.'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) return 'Enter a valid email.'
    if (!form.password)         return 'Password is required.'
    if (form.password.length<6) return 'Password must be at least 6 characters.'
    if (form.password !== form.confirm) return 'Passwords do not match.'
    return null
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError(''); setSuccess('')
    const err = validate(); if (err) { setError(err); return }
    setLoading(true)
    try {
      const res  = await fetch('/api/auth/register', {
        method:      'POST',
        headers:     { 'Content-Type': 'application/json' },
        credentials: 'include',
        body:        JSON.stringify({
          username: form.username.trim(),
          email:    form.email.trim(),
          password: form.password,
          role:     form.role,
        }),
      })
      const data = await res.json().catch(() => ({}))
      if (res.ok || res.status === 201) {
        setSuccess(`Account created for ${data.username || form.username}! You can now sign in.`)
        setForm({username:'',email:'',password:'',confirm:'',role:'ROLE_USER'})
      } else {
        setError(data.message || `Registration failed (HTTP ${res.status})`)
      }
    } catch (err) { setError(`Network error: ${err.message}`)
    } finally { setLoading(false) }
  }

  return (
    <form onSubmit={handleSubmit} className="form" noValidate>
      {error   && <div className="error-box">{error}</div>}
      {success && <div className="success-box">{success}
        <button type="button" className="success-box__link" onClick={onSwitchToLogin}>Go to Login →</button>
      </div>}
      <div className="field"><label className="label">USERNAME</label>
        <input className="input" type="text" placeholder="choose_a_username"
          value={form.username} onChange={set('username')} autoComplete="username"/></div>
      <div className="field"><label className="label">EMAIL</label>
        <input className="input" type="email" placeholder="you@example.com"
          value={form.email} onChange={set('email')} autoComplete="email"/></div>
      <div className="field"><label className="label">PASSWORD</label>
        <input className="input" type="password" placeholder="min. 6 characters"
          value={form.password} onChange={set('password')} autoComplete="new-password"/></div>
      <div className="field"><label className="label">CONFIRM PASSWORD</label>
        <input className="input" type="password" placeholder="repeat password"
          value={form.confirm} onChange={set('confirm')} autoComplete="new-password"/></div>
      <div className="field"><label className="label">ROLE</label>
        <select className="input input--select" value={form.role} onChange={set('role')}>
          <option value="ROLE_USER">User</option>
          <option value="ROLE_ADMIN">Admin</option>
        </select></div>
      <button className="btn-primary" type="submit" disabled={loading}>
        {loading ? <span className="spinner"/> : 'CREATE ACCOUNT'}
      </button>
    </form>
  )
}

/* ── OAuth2 Panel ───────────────────────────────────────── */
function OAuth2Panel({ gateway }) {
  return (
    <div className="oauth-panel">
      <p className="oauth-hint">Continue with a third-party provider</p>
      <button className="btn-oauth"
        onClick={() => window.location.href=`${gateway}/oauth2/authorization/google`}>
        <GoogleIcon/> Continue with Google
      </button>
      <button className="btn-oauth"
        onClick={() => window.location.href=`${gateway}/oauth2/authorization/github`}>
        <GitHubIcon/> Continue with GitHub
      </button>
    </div>
  )
}

function GoogleIcon() {
  return (<svg width="18" height="18" viewBox="0 0 48 48" fill="none">
    <path d="M47.5 24.6c0-1.6-.1-3.2-.4-4.6H24v8.7h13.2c-.6 3-2.3 5.5-4.9 7.2v6h7.9c4.6-4.3 7.3-10.6 7.3-17.3z" fill="#4285F4"/>
    <path d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.9-6c-2.2 1.5-5 2.3-8 2.3-6.1 0-11.3-4.1-13.2-9.7H2.7v6.2C6.7 42.9 14.8 48 24 48z" fill="#34A853"/>
    <path d="M10.8 28.8c-.5-1.5-.8-3-.8-4.8s.3-3.3.8-4.8v-6.2H2.7C1 16.5 0 20.1 0 24s1 7.5 2.7 10.9l8.1-6.1z" fill="#FBBC05"/>
    <path d="M24 9.5c3.4 0 6.5 1.2 8.9 3.5l6.6-6.6C35.9 2.4 30.5 0 24 0 14.8 0 6.7 5.1 2.7 13.1l8.1 6.2C12.7 13.6 17.9 9.5 24 9.5z" fill="#EA4335"/>
  </svg>)
}

function GitHubIcon() {
  return (<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
    <path d="M12 0C5.37 0 0 5.37 0 12c0 5.3 3.44 9.8 8.2 11.4.6.1.82-.26.82-.58v-2.03c-3.34.73-4.04-1.61-4.04-1.61-.55-1.39-1.34-1.76-1.34-1.76-1.09-.74.08-.73.08-.73 1.2.09 1.84 1.24 1.84 1.24 1.07 1.83 2.8 1.3 3.49 1 .1-.78.42-1.3.76-1.6-2.67-.3-5.47-1.33-5.47-5.93 0-1.31.47-2.38 1.24-3.22-.14-.3-.54-1.52.1-3.18 0 0 1.01-.32 3.3 1.23a11.5 11.5 0 0 1 3-.4c1.02 0 2.04.14 3 .4 2.28-1.55 3.29-1.23 3.29-1.23.65 1.66.24 2.88.12 3.18.77.84 1.24 1.91 1.24 3.22 0 4.61-2.81 5.63-5.48 5.92.43.37.81 1.1.81 2.22v3.29c0 .32.22.7.83.58C20.56 21.8 24 17.3 24 12c0-6.63-5.37-12-12-12z"/>
  </svg>)
}