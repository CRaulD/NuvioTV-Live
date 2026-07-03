package com.nuvio.tv.core.server

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object IptvWebPage {

    fun getHtml(baseContext: Context): String {
        val tag = baseContext.getSharedPreferences("app_locale", Context.MODE_PRIVATE)
            .getString("locale_tag", null)
        val context = if (!tag.isNullOrEmpty()) {
            val config = Configuration(baseContext.resources.configuration)
            config.setLocale(Locale.forLanguageTag(tag))
            baseContext.createConfigurationContext(config)
        } else baseContext

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<title>${context.getString(com.nuvio.tv.R.string.app_name)} - IPTV Config</title>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800;900&display=swap" rel="stylesheet">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  *:focus, *:active { outline: none !important; }
  body {
    font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    background: #000; color: #fff; min-height: 100vh; line-height: 1.5;
  }
  .page { max-width: 600px; margin: 0 auto; padding: 0 1.5rem 6rem; }
  .header {
    text-align: center; padding: 3rem 0 2.5rem;
    border-bottom: 1px solid rgba(255,255,255,0.05); margin-bottom: 2.5rem;
  }
  .header h1 { font-size: 1.5rem; font-weight: 800; letter-spacing: -0.03em; margin-bottom: 0.25rem; }
  .header p { font-size: 0.875rem; font-weight: 300; color: rgba(255,255,255,0.4); }
  .field { margin-bottom: 1.5rem; }
  .field label {
    display: block; font-size: 0.75rem; font-weight: 500;
    color: rgba(255,255,255,0.3); letter-spacing: 0.1em;
    text-transform: uppercase; margin-bottom: 0.75rem;
  }
  .field input {
    width: 100%; background: transparent;
    border: 1px solid rgba(255,255,255,0.12); border-radius: 100px;
    padding: 0.875rem 1.25rem; color: #fff; font-family: inherit;
    font-size: 0.9rem; font-weight: 400; transition: border-color 0.3s ease;
  }
  .field input:focus { border-color: rgba(255,255,255,0.4); }
  .field input::placeholder { color: rgba(255,255,255,0.2); }
  .btn {
    display: inline-flex; align-items: center; justify-content: center;
    background: transparent; border: 1px solid rgba(255,255,255,0.2);
    border-radius: 100px; padding: 0.875rem 1.5rem; color: #fff;
    font-family: inherit; font-size: 0.875rem; font-weight: 500;
    cursor: pointer; transition: all 0.3s ease; white-space: nowrap;
    -webkit-tap-highlight-color: transparent;
  }
  .btn:hover { background: #fff; color: #000; border-color: #fff; }
  .btn:active { transform: scale(0.97); }
  .btn-save {
    width: 100%; padding: 1rem; font-size: 0.95rem; font-weight: 600; margin-top: 1rem;
  }
  .btn-save:disabled {
    opacity: 0.2; cursor: not-allowed; pointer-events: none;
  }
  .status-overlay {
    position: fixed; top: 0; left: 0; width: 100%; height: 100%;
    background: rgba(0,0,0,0.92); backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px); z-index: 500;
    display: flex; align-items: center; justify-content: center;
    opacity: 0; visibility: hidden; transition: all 0.3s ease;
  }
  .status-overlay.visible { opacity: 1; visibility: visible; }
  .status-content { text-align: center; max-width: 340px; padding: 2rem; }
  .status-icon { margin-bottom: 1.5rem; }
  .spinner {
    width: 40px; height: 40px;
    border: 2px solid rgba(255,255,255,0.1); border-top-color: #fff;
    border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto;
  }
  @keyframes spin { to { transform: rotate(360deg); } }
  .status-title { font-size: 1.25rem; font-weight: 700; letter-spacing: -0.02em; margin-bottom: 0.5rem; }
  .status-message { font-size: 0.875rem; font-weight: 300; color: rgba(255,255,255,0.4); line-height: 1.6; }
  .status-success .status-title { color: #fff; }
  .status-error .status-title { color: rgba(207,102,121,0.9); }
  .status-svg { width: 40px; height: 40px; margin: 0 auto; }
  .status-svg svg { width: 40px; height: 40px; }
  .connection-bar {
    position: fixed; top: 0; left: 0; right: 0;
    background: rgba(207,102,121,0.15); border-bottom: 1px solid rgba(207,102,121,0.3);
    padding: 0.75rem 1.5rem; text-align: center; font-size: 0.8rem;
    font-weight: 500; color: rgba(207,102,121,0.9); z-index: 600; display: none;
  }
  .connection-bar.visible { display: block; }
  @media (max-width: 480px) {
    .page { padding: 0 1rem 5rem; }
    .header { padding: 2rem 0 2rem; }
  }
</style>
</head>
<body>
<div class="page">
  <div class="header">
    <h1>TV ao Vivo</h1>
    <p>Configure sua playlist IPTV pelo celular</p>
  </div>

  <div class="field">
    <label>URL da Playlist M3U</label>
    <input type="url" id="m3uUrl" placeholder="https://m3u4u.com/m3u/..." autocomplete="off" autocapitalize="off" spellcheck="false">
  </div>

  <div class="field">
    <label>URL do EPG (opcional)</label>
    <input type="url" id="epgUrl" placeholder="https://m3u4u.com/xml/..." autocomplete="off" autocapitalize="off" spellcheck="false">
  </div>

  <button class="btn btn-save" id="saveBtn" onclick="saveChanges()">Salvar</button>
</div>

<div class="status-overlay" id="statusOverlay">
  <div class="status-content" id="statusContent"></div>
</div>

<div class="connection-bar" id="connectionBar">Conexão perdida com a TV</div>

<script>
var pollTimer = null;
var connectionLost = false;

async function loadConfig() {
  try {
    var res = await fetchWithTimeout('/api/iptv', {}, 5000);
    var config = await res.json();
    document.getElementById('m3uUrl').value = config.m3uUrl || '';
    document.getElementById('epgUrl').value = config.epgUrl || '';
    setConnectionLost(false);
  } catch (e) {
    setConnectionLost(true);
  }
}

async function fetchWithTimeout(url, opts, timeoutMs) {
  var controller = new AbortController();
  var timer = setTimeout(function() { controller.abort(); }, timeoutMs);
  try {
    var o = opts || {};
    o.signal = controller.signal;
    return await fetch(url, o);
  } finally {
    clearTimeout(timer);
  }
}

function setConnectionLost(lost) {
  connectionLost = lost;
  document.getElementById('connectionBar').className = 'connection-bar' + (lost ? ' visible' : '');
}

async function saveChanges() {
  var saveBtn = document.getElementById('saveBtn');
  saveBtn.disabled = true;

  var m3uUrl = document.getElementById('m3uUrl').value.trim();
  var epgUrl = document.getElementById('epgUrl').value.trim();

  try {
    var res = await fetchWithTimeout('/api/iptv', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ m3uUrl: m3uUrl, epgUrl: epgUrl })
    }, 8000);
    var data = await res.json();

    if (data.status === 'pending_confirmation') {
      showPendingStatus();
      pollStatus(data.id);
    } else if (data.error) {
      showErrorStatus(data.error);
      saveBtn.disabled = false;
    }
  } catch (e) {
    showErrorStatus('Erro ao salvar. Tente novamente.');
    saveBtn.disabled = false;
  }
}

function showPendingStatus() {
  var overlay = document.getElementById('statusOverlay');
  var content = document.getElementById('statusContent');
  content.innerHTML =
    '<div class="status-icon"><div class="spinner"></div></div>' +
    '<div class="status-title">Aguardando confirmação na TV</div>' +
    '<div class="status-message">Confirme as alterações no aparelho de TV</div>';
  content.className = 'status-content';
  overlay.classList.add('visible');
}

function showSuccessStatus() {
  var content = document.getElementById('statusContent');
  content.innerHTML =
    '<div class="status-icon"><div class="status-svg"><svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg></div></div>' +
    '<div class="status-title">Playlist atualizada!</div>' +
    '<div class="status-message">As novas URLs foram salvas na TV</div>';
  content.className = 'status-content status-success';
  setTimeout(dismissStatus, 2500);
}

function showErrorStatus(msg) {
  var content = document.getElementById('statusContent');
  content.innerHTML =
    '<div class="status-icon"><div class="status-svg"><svg viewBox="0 0 24 24" fill="none" stroke="rgba(207,102,121,0.9)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6L6 18M6 6l12 12"/></svg></div></div>' +
    '<div class="status-title">Erro</div>' +
    '<div class="status-message">' + msg + '</div>';
  content.className = 'status-content status-error';
  saveBtn.disabled = false;
}

function dismissStatus() {
  document.getElementById('statusOverlay').classList.remove('visible');
}

function pollStatus(id) {
  pollTimer = setInterval(async function() {
    try {
      var res = await fetchWithTimeout('/api/status/' + id, {}, 5000);
      var data = await res.json();
      if (data.status === 'confirmed') {
        clearInterval(pollTimer);
        showSuccessStatus();
      } else if (data.status === 'rejected' || data.status === 'not_found') {
        clearInterval(pollTimer);
        showErrorStatus('Alterações rejeitadas na TV');
        saveBtn.disabled = false;
      }
    } catch (e) {
      // keep polling
    }
  }, 1500);
}

loadConfig();
</script>
</body>
</html>"""
    }
}
