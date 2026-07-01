package com.nuvio.tv.core.server

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object TvLoginWebPage {

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
<title>${context.getString(com.nuvio.tv.R.string.app_name)} - Login</title>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800;900&display=swap" rel="stylesheet">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  *:focus, *:active { outline: none !important; }
  body {
    font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    background: #000; color: #fff; min-height: 100vh; line-height: 1.5;
    display: flex; align-items: center; justify-content: center;
  }
  .page { max-width: 400px; width: 100%; padding: 2rem 1.5rem; }
  .header { text-align: center; margin-bottom: 2.5rem; }
  .header h1 { font-size: 1.75rem; font-weight: 800; letter-spacing: -0.03em; margin-bottom: 0.25rem; }
  .header p { font-size: 0.875rem; font-weight: 300; color: rgba(255,255,255,0.4); }
  .field { margin-bottom: 1.25rem; }
  .field label {
    display: block; font-size: 0.75rem; font-weight: 500;
    color: rgba(255,255,255,0.3); letter-spacing: 0.1em;
    text-transform: uppercase; margin-bottom: 0.5rem;
  }
  .field input {
    width: 100%; background: rgba(255,255,255,0.06);
    border: 1px solid rgba(255,255,255,0.12); border-radius: 12px;
    padding: 1rem 1.25rem; color: #fff; font-family: inherit;
    font-size: 1rem; font-weight: 400; transition: border-color 0.3s ease;
  }
  .field input:focus { border-color: rgba(255,255,255,0.4); }
  .field input::placeholder { color: rgba(255,255,255,0.2); }
  .btn {
    width: 100%; display: inline-flex; align-items: center; justify-content: center;
    background: #E50914; border: none; border-radius: 12px;
    padding: 1rem; color: #fff; font-family: inherit; font-size: 1rem;
    font-weight: 600; cursor: pointer; transition: all 0.3s ease;
    margin-top: 0.5rem;
  }
  .btn:hover { background: #FF2A2A; }
  .btn:active { transform: scale(0.97); }
  .btn:disabled { opacity: 0.3; cursor: not-allowed; pointer-events: none; }
  .error-msg {
    color: #FF6E6E; font-size: 0.85rem; text-align: center;
    margin-top: 1rem; display: none;
  }
  .success-msg {
    text-align: center; padding: 2rem 0;
  }
  .success-msg .check {
    width: 64px; height: 64px; margin: 0 auto 1.5rem;
    background: rgba(255,255,255,0.1); border-radius: 50%;
    display: flex; align-items: center; justify-content: center;
  }
  .success-msg .check svg { width: 32px; height: 32px; }
  .success-msg h2 { font-size: 1.5rem; font-weight: 700; margin-bottom: 0.5rem; }
  .success-msg p { font-size: 0.875rem; color: rgba(255,255,255,0.4); }
  .spinner {
    width: 20px; height: 20px; display: inline-block;
    border: 2px solid rgba(255,255,255,0.3); border-top-color: #fff;
    border-radius: 50%; animation: spin 0.8s linear infinite;
    vertical-align: middle; margin-right: 0.5rem;
  }
  @keyframes spin { to { transform: rotate(360deg); } }
</style>
</head>
<body>
<div class="page" id="page">
  <div class="header">
    <h1>NuvioTV</h1>
    <p>Faça login na sua TV</p>
  </div>

  <div id="loginForm">
    <div class="field">
      <label>Email</label>
      <input type="email" id="email" placeholder="seu@email.com" autocomplete="email" autocapitalize="off" spellcheck="false" inputmode="email">
    </div>

    <div class="field">
      <label>Senha</label>
      <input type="password" id="password" placeholder="••••••••" autocomplete="current-password">
    </div>

    <button class="btn" id="loginBtn" onclick="doLogin()">Entrar</button>
    <div class="error-msg" id="errorMsg"></div>
  </div>

  <div id="successState" style="display:none">
    <div class="success-msg">
      <div class="check">
        <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M20 6L9 17l-5-5"/>
        </svg>
      </div>
      <h2>Logado com sucesso!</h2>
      <p>Você já pode fechar esta página e voltar para a TV</p>
    </div>
  </div>
</div>

<script>
async function doLogin() {
  var email = document.getElementById('email').value.trim();
  var password = document.getElementById('password').value;
  var btn = document.getElementById('loginBtn');
  var errorEl = document.getElementById('errorMsg');

  if (!email || !password) {
    errorEl.textContent = 'Preencha email e senha';
    errorEl.style.display = 'block';
    return;
  }

  errorEl.style.display = 'none';
  btn.disabled = true;
  btn.innerHTML = '<span class="spinner"></span> Entrando...';

  try {
    var res = await fetch('/api/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email, password: password })
    });
    var data = await res.json();

    if (data.status === 'ok') {
      document.getElementById('loginForm').style.display = 'none';
      document.getElementById('successState').style.display = 'block';
    } else {
      errorEl.textContent = data.message || 'Erro ao fazer login';
      errorEl.style.display = 'block';
      btn.disabled = false;
      btn.innerHTML = 'Entrar';
    }
  } catch (e) {
    errorEl.textContent = 'Erro de conexão com a TV';
    errorEl.style.display = 'block';
    btn.disabled = false;
    btn.innerHTML = 'Entrar';
  }
}
</script>
</body>
</html>"""
    }
}
