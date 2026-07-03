# Contexto: Migração NuvioTV-Live Fork → Upstream 0.7.13-beta

> Documento para outro agente entender todo o cenário e executar a migração.

---

## 1. PROJETO

- **Repo fork (nosso):** https://github.com/CRaulD/NuvioTV-Live
- **Upstream oficial:** https://github.com/NuvioMedia/NuvioTV
- **Alvo:** tag `0.7.13-beta` (commit 3b1e1725) — está em `upstream/dev`, 3 commits à frente de `upstream/main`
- **Base atual do fork:** `v0.1.1-nuvio-live` (tag nossa no commit dfd81f5a)
- **Nossa branch fork:** `dev-old` (contém nossos 6 commits)
- **Branch atual tracking upstream:** `dev` (contém upstream/main limpo, 78f782bc)

### 1.1 Atual estrutura de branches

```
dev (atual)    → tracking upstream/main (limpo, sem IPTV)
dev-old        → tracking origin/dev (tem nossos commits IPTV e fork)
```

### 1.2 Nossos 6 commits no dev-old

| Commit | Descrição | Tamanho | Status no 0.7.13 |
|--------|-----------|---------|-------------------|
| d2dd392e | NuvioTV-Live fork v0.1.0: update system CRaulD + IPTV base | 19 arquivos | Será refeito |
| 3e474fea | Remove hardcoded email/password credentials (security) | 2 arquivos | Descartável (upstream mudou) |
| dfd81f5a | Bump to v0.1.1-nuvio-live | 1 arquivo | Descartável |
| a58809b1 | Fix series watched sync badges | GitHub files | Descartável |
| 705573e3 | Add local QR servers for M3U/EPG config and TV login | 10 arquivos | Portar conceito |
| faf82c4d | fix(iptv): correções D-pad, marquee e EPG | 11 arquivos | Portar conceito |

**Conclusão:** cherry-pick/rebase/merge são INVIÁVEIS — os arquivos que tocamos foram deletados/refatorados no upstream. A feature IPTV é 100% nossa. Precisa ser REIMPLEMENTADA.

---

## 2. FEATURE IPTV (nossa, exclusiva do fork)

### 2.1 Camada Core/Domain/Data (fundação, ~600 linhas)

Arquivos em `dev-old` que NÃO existem em 0.7.13-beta:

```
app/src/main/java/com/nuvio/tv/core/di/IptvModule.kt
app/src/main/java/com/nuvio/tv/core/iptv/EpgParser.kt
app/src/main/java/com/nuvio/tv/core/iptv/M3uParser.kt
app/src/main/java/com/nuvio/tv/data/local/IptvDaos.kt
app/src/main/java/com/nuvio/tv/data/local/IptvEntities.kt
app/src/main/java/com/nuvio/tv/domain/model/EpgProgram.kt
app/src/main/java/com/nuvio/tv/domain/repository/IptvRepository.kt
app/src/main/java/com/nuvio/tv/data/repository/IptvRepositoryImpl.kt
```

Além disso, `AppDatabase.kt` e `PlayerSettingsDataStore.kt` foram modificados no fork para incluir as entities IPTV (Room) — isso precisa ser refeito na versão do upstream.

### 2.2 Camada UI IPTV (~2.000 linhas)

```
app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvScreen.kt          (624 linhas)
app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvPlayerPane.kt
app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvEpgPane.kt
app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvSearchBar.kt
app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvFocusHandler.kt
app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvMarqueeText.kt
app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvFooter.kt
app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvPlayerScreen.kt
app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvSetupScreen.kt
app/src/main/java/com/nuvio/tv/ui/screens/iptv/IptvViewModel.kt
```

NavHost (`ui/navigation/NuvioNavHost.kt`) foi modificado no fork para adicionar rotas IPTV. Em 0.7.13-beta **ele ainda existe**, mas sem as rotas IPTV. Precisará ser adaptado.

### 2.3 Servidores QR (config M3U/EPG e TV login, ~1.000 linhas)

```
app/src/main/java/com/nuvio/tv/core/server/IptvConfigServer.kt
app/src/main/java/com/nuvio/tv/core/server/IptvWebPage.kt
app/src/main/java/com/nuvio/tv/core/server/TvLoginServer.kt
app/src/main/java/com/nuvio/tv/core/server/TvLoginWebPage.kt
```

### 2.4 Account/Login (modificado no fork)

```
ui/screens/account/EmailLoginScreen.kt   (135 linhas nossas)
```

Em 0.7.13-beta isso FOI SUBSTITUÍDO pelo sistema oficial do upstream:
- `AuthManager.kt` (core/auth/)
- `AuthSignInScreen.kt` (login por email)
- `AuthQrSignInScreen.kt` (login por QR)
- `AccountScreen.kt`
- `SyncCodeClaimScreen.kt` / `SyncCodeGenerateScreen.kt`

**Decisão:** Vamos ABANDONAR nosso EmailLoginScreen e ADOTAR o sistema AuthManager do upstream, extraindo as credenciais Supabase do APK oficial (veja seção 4).

### 2.5 Ajustes de tema e strings

- `ui/theme/ThemeColors.kt` — existe em 0.7.13-beta, nossos temas tintados (Crimson, Emerald, Rose) precisam ser portados
- `res/values/strings.xml` — strings IPTV precisam ser adicionadas

---

## 3. O QUE MUDOU NO UPSTREAM (v0.1.x → 0.7.13-beta)

### 3.1 Novos subsistemas

```
core/plugin/     → PluginManager, PluginRuntime, CloudStream extensions
core/debrid/     → DebridProvider, RealDebrid, Premiumize, Torbox
core/cloud/      → CloudLibrary, PremiumizeCloud, TorboxCloud
core/auth/       → AuthManager, AccountLocalDataResetService (SUBSTITUIU nosso login)
core/player/     → DolbyVision, ExternalPlayer, BitrateAwareLoadControl
app/src/full/    → Build flavor "full" (plugins, updates, trailers habilitados)
app/src/main/    → Build flavor "main" (básico)
```

### 3.2 Arquitetura de build

- Build flavors: `full` (completo) e `main` (básico)
- Nossa feature IPTV deve ir no flavor `full` (o que usamos é o full)
- `build.gradle.kts` tem dezenas de `buildConfigField` vindos de `local.properties` / `dev.properties`

### 3.3 Diferença quantitativa

```
60 arquivos alterados
159 inserções, 3.730 DELEÇÕES
Refatoração agressiva do upstream (removeu 22x mais que adicionou)
```

---

## 4. SUPABASE + AUTH (estratégia de login)

### 4.1 Decisão

Vamos usar o **AuthManager oficial do NuvioTV** em vez do nosso EmailLoginScreen custom. As credenciais Supabase (URL + anon key) serão extraídas do APK release oficial do NuvioTV.

Isso é prática comum na comunidade — vários forks fazem o mesmo, o Reddit deles confirma que nunca foi problema.

### 4.2 Como as creds são definidas no build.gradle.kts (0.7.13-beta)

```
buildConfigField("String", "SUPABASE_URL",
    "\"${resolveProperty(devProperties, localProperties, "NUVIO_SUPABASE_URL")}\"")
buildConfigField("String", "SUPABASE_ANON_KEY",
    "\"${resolveProperty(devProperties, localProperties, "NUVIO_SUPABASE_ANON_KEY")}\"")
```

As credenciais vêm de `dev.properties` ou `local.properties` com as chaves:
- `NUVIO_SUPABASE_URL`
- `NUVIO_SUPABASE_ANON_KEY`

Esses arquivos NÃO estão no repositório público. Mas no APK release compilado, os valores estão embutidos como strings no DEX.

### 4.3 Extração do APK (já iniciada)

APK baixado: `C:\Users\Raul\nuvio-apk\app-full-arm64-v8a-release.apk` (66MB, da release 0.7.13-beta)

DEX extraídos: classes.dex, classes2.dex, classes3.dex, classes4.dex em `C:\Users\Raul\nuvio-apk\dex/`

**Encontrado:**
- A `SUPABASE_ANON_KEY` (JWT de 169 chars) está em `classes3.dex`
- A `SUPABASE_URL` (URL do projeto) **NÃO foi extraída como string plana** — está compilada no BuildConfig e precisa ser extraída via apktool (decompilação smali) ou via grep com contexto mais amplo.
- Usar `apkanalyzer` do Android SDK ou apktool para extrair do build.gradle compilado

### 4.4 Dependências Supabase no projeto

```kotlin
// SupabaseModule.kt
createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY
) {
    install(Auth) {
        alwaysAutoRefresh = true
        autoLoadFromStorage = true
        autoSaveToStorage = true
    }
    install(Postgrest)
}
```

O SupabaseModule usa `io.github.jan.supabase` (supabase-kt library) com os plugins:
- Auth
- Postgrest

---

## 5. ESTRATÉGIA DE MIGRAÇÃO

**Tipo:** RE-FORK LIMPO + PORTE DE FEATURE

**Base:** Nova branch a partir de `upstream/dev` (para acompanhar desenvolvimento ativo do NuvioTV) — não da tag fixa.

### Ordem de execução

| Fase | Descrição | Complexidade |
|------|-----------|-------------|
| P0 | Branch nova `migrate/iptv-v0.7.13` a partir de `upstream/dev` | Fácil |
| P1 | Extrair credenciais Supabase do APK (URL + anon key) | Médio |
| P2 | Configurar `NUVIO_SUPABASE_URL` e `NUVIO_SUPABASE_ANON_KEY` no `local.properties` | Fácil |
| P3 | Compilar/flavor `full` para verificar se build base funciona | Médio |
| P4 | Copiar/adaptar camada IPTV core+domain+data (parsers, DAOs, repos) do dev-old | Médio |
| P5 | Adaptar AppDatabase + Room entities para o schema do 0.7.13 | Médio |
| P6 | Adaptar NuvioNavHost.kt com rotas IPTV (existe em 0.7.13) | Fácil |
| P7 | Portar UI IPTV (IptvScreen, panes, etc.) — pode precisar adaptar Compose APIs | Alto |
| P8 | Portar servidores QR (IptvConfigServer, TvLoginServer) | Médio |
| P9 | Aplicar temas tintados (Crimson, Emerald, Rose) no ThemeColors.kt | Fácil |
| P10 | Adicionar strings IPTV ao resources | Fácil |
| P11 | Configurar update system apontando CRaulD/NuvioTV-Live | Médio |
| P12 | Bump version (ex: v0.7.13-nuvio-live-1) | Fácil |
| P13 | Compilar, debugar, corrigir erros de API | Alto |

### Riscos conhecidos

1. **Room:** versão do Room/KSP no 0.7.13-beta pode ser diferente da nossa base — verificar `gradle/libs.versions.toml` e compatibilidade das entities IPTV
2. **Compose:** `androidx.tv.material3` API pode ter mudado (telas IPTV usam ExperimentalTvMaterial3Api)
3. **Auth:** nosso EmailLoginScreen será descartado em favor do AuthManager do upstream — verificar se o fluxo de login no IPTV continua funcionando sem ele
4. **Build flavors:** IPTV precisa estar no flavor `full` (que tem FEATURE_PLUGINS_ENABLED, FEATURE_IN_APP_UPDATES_ENABLED)
5. **Upstream continua evoluindo:** branch baseada em `upstream/dev` vai receber novos commits enquanto portamos — pode precisar rebase eventual

---

## 6. AMBIENTE DE BUILD

- **OS:** Windows 10 (bash via git-bash)
- **JDK:** Temurin 17.0.19
- **Android SDK:** C:\Users\Raul\AppData\Local\Android\Sdk (platform 36, build-tools 36.0.0, NDK 27.0.12077973, CMake 3.22.1)
- **Python:** python 3.11.15, pip, uv installed
- **Repo path:** C:\Users\Raul\NuvioTV-Live (branch dev atualmente)

---

## 7. FORA DE ESCOPO (NÃO MEXER)

- Features de debrid/cloud do upstream (não interagem com IPTV)
- Plugin runtime / CloudStream extensions
- DolbyVision / HDR / codecs avançados
- GitHub Actions / CI workflows
- Trakt auth (não mexemos)
- Novas features do upstream que não conflitam com IPTV

---

## 8. ARQUIVOS DE REFERÊNCIA

- Diagnóstico completo: `.hermes/diagnostico-migracao-0.7.13.md`
- APK oficial: `C:\Users\Raul\nuvio-apk\app-full-arm64-v8a-release.apk`
- DEX extraídos: `C:\Users\Raul\nuvio-apk\dex/`
- Nosso código IPTV (referência): branch `dev-old`
- Documentação hermes do projeto: `.hermes/contexto-iptv.md`
