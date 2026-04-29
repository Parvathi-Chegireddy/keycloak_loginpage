import { createRoot } from 'react-dom/client'
import './styles.css'
import App from './App.jsx'

// StrictMode removed — it double-invokes every effect in development,
// which causes one request to fire WITHOUT a Bearer token (the real mount's
// call) while the successful token-bearing call gets discarded by cleanup.
// This is a known StrictMode + async-fetch interaction problem.
// Production builds never use StrictMode, so behaviour is identical there.
createRoot(document.getElementById('root')).render(<App />)