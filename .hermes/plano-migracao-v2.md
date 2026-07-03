# PLANO DE MIGRAÇÃO v2: NuvioTV-Live Fork → Upstream 0.7.13-beta

> Revisão: 2026-07-03
> Baseado em: diagnóstico-migracao-0.7.13.md + análise do repo upstream (GitHub NuvioMedia/NuvioTV)
> Substitui: plano-migracao.md (v1)

---

## MUDANÇAS DA v1 → v2

A análise do código-fonte do upstream (nuvio.tv/docs + GitHub NuvioMedia/NuvioTV/dev)
revelou que o upstream JÁ TEM sistema completo de login (email + QR) via Supabase.

Isso elimina a necessidade de portar nosso EmailLoginScreen e possivelmente
TvLoginServer/TvLoginWebPage (serão avaliados na Fase 3).

---

## ESTRATÉGIA GERAL

RE-FORK LIMPO + PORTE DE FEATURE IPTV

Branch: migrate/iptv-v0.7.13 (criada a partir de upstream/dev)
Feature IPTV (~3.600 linhas, 37 arquivos) copiada do dev-old como arquivos novos.
Login: AuthManager do upstream (email + QR via Supabase RPC).

---

## PROGRESSO ATUAL

| Fase | Status | Observação |
|------|--------|------------|
| Fase 0 | ✅ Concluída | Branch criada, backup dev-old |
| Fase 1 | ✅ Concluída | Build base compila (BUILD SUCCESSFUL) |
| Fase 2 | ✅ Concluída | 9 arquivos Core/Domain/Data + AppDatabase |
| Fase 3 | ⚠️ Revisar | 4 servers copiados, 2 provavelmente desnecessários |
| Fase 3.5 | ✅ Concluída | build.gradle + libs.versions (Room 2.7.0-rc01) |
| Fase 4 | Pendente | UI IPTV (10 arquivos + NavHost + Screen.kt) |
| Fase 5 | Pendente | Login/Supabase (simplificado — AuthManager do upstream) |
| Fase 6 | Pendente | Temas, strings, update system, bump version |
| Fase 7 | Pendente | Build final e teste |

**NADA FOI COMMITTED AINDA** — tudo está staged ou untracked.

---

## FASE 0 — Preparação ✅ CONCLUÍDA

- backup/dev-old criado
- branch migrate/iptv-v0.7.13 criada a partir de upstream/dev
- push pro origin feito

---

## FASE 1 — Build Base ✅ CONCLUÍDA

- assembleFullDebug: BUILD SUCCESSFUL (3m41s)
- APK gerado: app-full-arm64-v8a-debug.apk (87MB, timestamp 23:54)

---

## FASE 2 — Porte Core/Domain/Data ✅ CONCLUÍDA

Arquivos copiados (staged):
- core/di/IptvModule.kt (Hilt module: AppDatabase + DAOs + Repository)
- core/iptv/EpgParser.kt (parser XMLTV)
- core/iptv/M3uParser.kt (parser M3U)
- data/local/IptvEntities.kt (ConfigEntity, FavoriteEntity, EpgProgramEntity)
- data/local/IptvDaos.kt (ConfigDao, FavoriteDao, EpgDao)
- data/local/AppDatabase.kt (untracked, 14 linhas — Room database)
- data/repository/IptvRepositoryImpl.kt
- domain/model/EpgProgram.kt
- domain/model/TvChannel.kt
- domain/repository/IptvRepository.kt

**Validação:** ConfigEntity, ConfigDao, FavoriteEntity, FavoriteDao, EpgDao
estão todos dentro de IptvEntities.kt e IptvDaos.kt (não em arquivos separados).
AppDatabase.kt referencia essas classes corretamente. SEM referências quebradas.

---

## FASE 3 — Porte dos Servidores QR ⚠️ REVISAR

Arquivos copiados (staged):
- IptvConfigServer.kt ✅ MANTER (serve página de config M3U/EPG via QR)
- IptvWebPage.kt ✅ MANTER (HTML da página de config)
- TvLoginServer.kt ⚠️ REMOVER (redundante — AuthManager do upstream já faz login QR)
- TvLoginWebPage.kt ⚠️ REMOVER (redundante — AuthQrSignInScreen do upstream já existe)

**Ação 3.1:** git reset HEAD app/src/main/java/com/nuvio/tv/core/server/TvLoginServer.kt
**Ação 3.2:** git reset HEAD app/src/main/java/com/nuvio/tv/core/server/TvLoginWebPage.kt
**Ação 3.3:** rm os arquivos (ou manter pra referência, mas fora do build)
**Ação 3.4:** Build de validação pós-limpeza

NanoHTTPD já existe no upstream (2.3.1) — IptvConfigServer deve funcionar sem
mudanças de dependência.

---

## FASE 3.5 — Build Config ✅ CONCLUÍDA (com correções pendentes)

build.gradle.kts (modificado):
- Room deps adicionadas (ksp, runtime, ktx, compiler) ✅
- ⚠️ CORRIGIR: signingConfig removido do debug build type (reverter)
- ⚠️ CORRIGIR: release key password alterado de "815787" para "123456" (reverter)

libs.versions.toml (modificado):
- room = "2.7.0-rc01" adicionado ✅
- room-runtime, room-ktx, room-compiler mapeados ✅

**Ação 3.5.1:** Reverter signingConfig e release key password no build.gradle.kts

---

## FASE 4 — Porte da UI IPTV

**4.1** Criar diretório: app/src/main/java/com/nuvio/tv/ui/screens/iptv/

**4.2** Copiar do dev-old (10 arquivos):
```
git checkout dev-old -- app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvScreen.kt
git checkout dev-old -- app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvPlayerPane.kt
git checkout dev-old -- app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvEpgPane.kt
git checkout dev-old -- app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvSearchBar.kt
git checkout dev-old -- app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvFocusHandler.kt
git checkout dev-old -- app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvMarqueeText.kt
git checkout dev-old -- app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvFooter.kt
git checkout dev-old -- app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvPlayerScreen.kt
git checkout dev-old -- app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvSetupScreen.kt
git checkout dev-old -- app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvViewModel.kt
```

**4.3** Modificar NuvioNavHost.kt — adicionar rotas IPTV:
O diff dev-old vs upstream mostra as 3 rotas a adicionar:
- Screen.Iptv → IptvScreen (com onChannelClick e onSetupClick)
- Screen.IptvSetup → IptvSetupScreen (com onSave)
- IptvPlayerScreen.ROUTE → IptvPlayerScreen (com args channelUrl/Name/Logo)
Aplicar via patch (NÃO copiar arquivo inteiro — o upstream tem rotas novas de debrid/cloud)

**4.4** Modificar Screen.kt — adicionar 2 sealed class entries:
- data object Iptv : Screen("iptv")
- data object IptvSetup : Screen("iptv_setup")
Aplicar via patch (inserir antes de Trakt)

**4.5** Verificar imports quebrados:
- androidx.tv.material3 (ExperimentalTvMaterial3Api) — já existe no upstream
- ViewModel/Hilt — já existe no upstream
- NuvioTheme.colors.* — já existe no upstream

**4.6** Compilar — corrigir erros de API
**4.7** [CHECKPOINT] UI IPTV compilando

---

## FASE 5 — Autenticação/Login (SIMPLIFICADA)

A descoberta-chave: o AuthManager do upstream JÁ TEM login completo:
- signInWithEmail() via Supabase Auth
- startTvLoginSession() + pollTvLoginSession() via Supabase RPC
- QR code → nuvio.tv/tv-login → usuário digita código → TV polla → tokens

**5.1** Configurar BuildConfig SUPABASE_URL e SUPABASE_ANON_KEY:
- Em local.properties: NUVIO_SUPABASE_URL=... e NUVIO_SUPABASE_ANON_KEY=...
- Anon key: JÁ TEMOS
- URL: extrair do APK oficial (C:\Users\Raul\nuvio-apk\) ou buscar nas preferências

**5.2** Verificar AuthQrSignInScreen + AuthSignInScreen do upstream funcionam:
- Já estão no upstream/dev (não precisam ser portadas)
- Dependem de AccountViewModel → AuthManager → SupabaseModule → Supabase client

**5.3** Adaptar IptvSetupScreen se necessário:
- No fork antigo pedia login via TvLoginServer nosso
- Agora: se usuário não tem login, redirecionar para AuthSignInScreen/AuthQrSignInScreen
- Ou simplesmente não exigir login para IPTV (config via QR do IptvConfigServer)

**5.4** [CHECKPOINT] Login funcional

---

## FASE 6 — Ajustes Finais

**6.1** Portar PlayerSettingsDataStore.kt (correções do dev-old):
- Legacy int→float migration (remove old int-only keys que causam ClassCastException)
- .catch com fallback (emite PlayerSettings() em vez de crashar)
- Log.e de erro

**6.2** Aplicar temas tintados:
- ThemeColors.kt já existe em 0.7.13
- Portar Crimson, Emerald, Rose tints do dev-old

**6.3** Adicionar strings IPTV ao res/values/strings.xml

**6.4** Configurar update system:
- GITHUB_OWNER = "CRaulD"
- GITHUB_REPO = "NuvioTV-Live"
- Em flavor full, AppFeaturePolicy.kt determina URLs de update

**6.5** Bump version:
- versionCode = sequencial a partir de 1012
- versionName = "0.7.13-nuvio-live-1"

**6.6** Remover arquivos obsoletos:
- TvLoginServer.kt, TvLoginWebPage.kt (se não removidos na Fase 3)

---

## FASE 7 — Build Final e Teste

**7.1** ./gradlew assembleFullDebug

**7.2** Corrigir eventuais erros finais

**7.3** Gerar APK:
```
app/build/outputs/apk/full/debug/app-full-arm64-v8a-debug.apk
```

**7.4** Instalar no emulador/dispositivo e testar:
- Login via Supabase (email + QR)
- IPTV: M3U/EPG config via QR (IptvConfigServer)
- IPTV: navegação D-pad, marquee, EPG
- Navegação geral do app

---

## ORDEM DE EXECUÇÃO IMEDIATA

1. Reverter build.gradle.kts (signingConfig + release key password) → Fase 3.5
2. Remover TvLoginServer + TvLoginWebPage do staging → Fase 3
3. Fazer commit do progresso atual (Fase 2 + Fase 3 limpa + Fase 3.5)
4. Portar UI IPTV (10 arquivos + NavHost + Screen.kt) → Fase 4
5. Build de validação → Fase 4 checkpoint
6. Extrair SUPABASE_URL do APK → Fase 5
7. Restante...

---

## FORA DE ESCOPO (NÃO vamos portar/reverter)

- EmailLoginScreen (CAIU do upstream, não portar)
- TvLoginServer/TvLoginWebPage (redundante com AuthManager do upstream)
- AndroidTvChannel sync (5 arquivos de integração com Live Channels do Android TV)
- DebugSyncBackendSwitch (feature do dev-old não essencial)
- Features do upstream que já vêm inclusas: Debrid, Cloud, Plugins, CI, traduções
- Histórico linear do fork antigo (aceitamos história limpa)

---

## DEPENDÊNCIAS EXTERNAS

| Item | Status | Observação |
|------|--------|------------|
| SUPABASE_URL | Pendente | Extrair do APK oficial |
| SUPABASE_ANON_KEY | ✅ Já temos | |
| APK oficial | ✅ Baixado | C:\Users\Raul\nuvio-apk\ |
| Código upstream | ✅ No repo | upstream/dev = migrate branch base |
| Código fork | ✅ No repo | dev-old |
| NanoHTTPD 2.3.1 | ✅ Já no upstream | Sem conflito |

---

## CRITÉRIOS DE ÊXITO

1. Build compila no flavor full com feature IPTV
2. Login via Supabase funcional (email + QR) usando AuthManager do upstream
3. IPTV funcional: M3U config, EPG, player, D-pad, marquee
4. Update system apontando CRaulD/NuvioTV-Live
5. Temas tintados Crimson/Emerald/Rose preservados
6. Código limpo (sem TvLoginServer/EmailLoginScreen morto)
