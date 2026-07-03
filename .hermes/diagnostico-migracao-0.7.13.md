# Diagnóstico: Migração Fork NuvioTV-Live → upstream 0.7.13-beta

Data: 2026-07-02
Autor: Hermes (com Raul)

---

## 1. PREMISSA CORRIGIDA

**IPTV é feature exclusiva do NOSSO fork.** O upstream NuvioTV é um app
de streaming debrid/cloud (RealDebrid, Premiumize, Torbox). IPTV (M3U/EPG,
QR config server, TV login) fomos NÓS que adicionamos no fork.

Logo: "migrar pra 0.7.13-beta" = pegar a base nova do upstream e
**re-portar** nossa feature IPTV do zero em cima dela. Não há patches
pra cherry-pick — a feature inteira é nossa e o upstream nunca a teve.

---

## 2. ESTADO DOS REPOSITÓRIOS

| Branch         | Tracking       | HEAD        | Contém IPTV? |
|----------------|----------------|-------------|--------------|
| dev (atual)    | upstream/main  | 78f782bc    | NÃO          |
| dev-old        | origin/dev     | faf82c4d    | SIM (nosso)  |

Tags do fork:    v0.1.0-nuvio-live, v0.1.1-nuvio-live, v0.1.2-nuvio-live
Tags upstream:  0.6.5→0.6.19-beta, 0.7.0→0.7.13-beta

Alvo: tag `0.7.13-beta` (commit 3b1e1725)
  - Está em upstream/dev (3 commits à frente do main)
  - upstream/dev já avançou 3 commits além da tag

---

## 3. INVENTÁRIO DA FEATURE IPTV (tudo nosso, em dev-old)

### 3.1 Core/Domain/Data (fundação, ~600 linhas)
- core/di/IptvModule.kt
- core/iptv/EpgParser.kt
- core/iptv/M3uParser.kt
- data/local/IptvDaos.kt
- data/local/IptvEntities.kt
- data/local/AppDatabase.kt (modificado — adicionou IPTV entities)
- data/local/PlayerSettingsDataStore.kt (modificado)
- data/repository/IptvRepositoryImpl.kt
- domain/model/EpgProgram.kt
- domain/repository/IptvRepository.kt

### 3.2 UI IPTV (telas, ~2.000 linhas)
- ui/screens/iptv/IptvScreen.kt         (624 linhas)
- ui/screens/iptv/IptvPlayerPane.kt
- ui/screens/iptv/IptvEpgPane.kt
- ui/screens/iptv/IptvSearchBar.kt
- ui/screens/iptv/IptvFocusHandler.kt
- ui/screens/iptv/IptvMarqueeText.kt
- ui/screens/iptv/IptvFooter.kt
- ui/screens/iptv/IptvPlayerScreen.kt
- ui/screens/iptv/IptvSetupScreen.kt
- ui/screens/iptv/IptvViewModel.kt
- ui/navigation/NuvioNavHost.kt (modificado — rotas IPTV)

### 3.3 Servers (QR config, ~1.000 linhas)
- core/server/IptvConfigServer.kt      (156 linhas)
- core/server/IptvWebPage.kt            (251 linhas)
- core/server/TvLoginServer.kt          (154 linhas)
- core/server/TvLoginWebPage.kt         (166 linhas)

### 3.4 Account/Login (modificado pelo fork)
- ui/screens/account/AccountUiState.kt
- ui/screens/account/AccountViewModel.kt
- ui/screens/account/EmailLoginScreen.kt  (135 linhas nossas — NÃO existe
  em 0.7.13-beta; upstream refez login via AuthManager)

### 3.5 Tema/Strings (pequenos ajustes)
- ui/theme/ThemeColors.kt               (ainda existe em 0.7.13)
- res/values/strings.xml                (strings IPTV nossas)

---

## 4. O QUE MUDOU NO UPSTREAM (v0.1.x → 0.7.13-beta)

60 arquivos alterados, 159 inserções, 3.730 DELEÇÕES.
O upstream REMOVEU 22x mais código do que adicionou = refatoração pesada.

Novos subsystems que talvez conflitem/complementem o IPTV:
- core/plugin/ (PluginManager, PluginRuntime, CloudStream extensions)
- core/debrid/ (DebridProvider et. al. — RealDebrid/Premiumize/Torbox)
- core/cloud/  (CloudLibrary, PremiumizeCloud, TorboxCloud)
- core/auth/   (AuthManager, AccountLocalDataResetService) ← SUBSTITUIU EmailLoginScreen
- core/player/ (DolbyVision, ExternalPlayer, BitrateAwareLoadControl)
- app/src/full/ (build flavors: full vs main — NÃO tínhamos isso)

PONTOS DE TENSÃO IDENTIFICADOS:
- Login:   upstream tem AuthManager — nosso EmailLoginScreen caiu. Precisa
           decidir: reaproveitar AuthManager ou portar EmailLoginScreen?
- NavHost: existe em 0.7.13 mas provavelmente com rotas novas (debrid).
           Nossas rotas IPTV precisam ser re-inseridas.
- DB:      AppDatabase.kt foi modificado p/ IPTV entities. Em 0.7.13 a
           versão do Room pode ter mudado (preciso conferir base_version).
- Build:   upstream adicionou flavors (full/main). Nossa build.gradle.kts
           precisa encaixar IPTV no flavor correto.

---

## 5. ESTRATÉGIA RECOMENDADA

Estratégia: RE-FORK LIMPO + PORTE DE FEATURE.

Passo 1 (P0): Branch nova a partir de 0.7.13-beta (`migrate/iptv-v0.7.13`).
Passo 2 (P1): Copiar a camada IPTV inteira (3.1, 3.2, 3.3) do dev-old
              como arquivos novos — não há conflito pq eles não existem lá.
Passo 3 (P2): Reintegrar rotas IPTV no NuvioNavHost.kt da 0.7.13.
Passo 4 (P3): Adicionar IPTV entities/DAOs ao AppDatabase da 0.7.13.
Passo 5 (P4): Build.gradle.kts: deps IPTV + flavors.
Passo 6 (P5): Decidir Account/Login: AuthManager OU portar EmailLoginScreen.
Passo 7 (P6): Strings, tema (ThemeColors já existe).
Passo 8 (P7): Compilar. Corrigir erros de API (Room, Compose, etc.).
Passo 9:     Update system apontando CRaulD/NuvioTV-Live + bump version.

JUSTIFICATIVA contra alternativas:
- Cherry-pick: INVIÁVEL (arquivos não existem no alvo).
- Rebase:      INVIÁVEL (mesma razão).
- Merge:       Gera conflitos insolúveis em 3.730 linhas deletadas.

---

## 6. RISCOS E PONTOS ABERTOS

R1) Versão do Room/KMP em 0.7.13 pode ser incompatível com nossos DAOs.
R2) Compose APIs podem ter mudado (versão do compose-bom).
R3) EmailLoginScreen: se upstream mudou o fluxo de account p/ AuthManager,
    nosso login por email+senha+QR pode precisar de adaptação profunda.
R4) Flavors (full/main): onde colocar IPTV? Provavelmente full.
R5) Updater: em 0.7.13 pode usar mecanismo diferente (UpdatePromptDialog
    apareceu em app/src/full/).

PERGUNTAS PRA RALUL (antes do plano formal):
Q1) Account/Login: reaproveitar AuthManager do upstream ou portar nosso
    EmailLoginScreen?
Q2) Iniciar branch nova a partir de 0.7.13-beta (tag fixa) ou upstream/dev
    (mais recente, 3 commits além)? Tag fixa é mais estável.
Q3) Quer AnaliseAPI detalhada das quebradas (Room/Compose/Auth) ANTES do
    plano, ou já confia no inventário acima?

---

## 7. FORA DE ESCOPO

- Features 0.7.x que NÃO interagem com IPTV (debrid, cloud, plugins).
- Reaproveitar commits dfd81f5a (bump), 3e474fea (remove creds),
  d2dd392e (update system) — serão refeitos do zero na nova base.
- Histórico linear do fork antigo. Aceitamos história limpa a partir de
  0.7.13-beta.
