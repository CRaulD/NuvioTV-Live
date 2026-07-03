# PLANO DE MIGRAÇÃO: NuvioTV-Live Fork → Upstream 0.7.13-beta

> Baseado em: diagnóstico-migracao-0.7.13.md, documento-migracao-para-agente.md

---

## ESTRATÉGIA GERAL

RE-FORK LIMPO + PORTE DE FEATURE IPTV

**Tipo:** Branch nova a partir de `upstream/dev` com feature IPTV copiada do `dev-old` e adaptada à nova arquitetura.

**Motivo:** Cherry-pick/rebase/merge são inviáveis — os arquivos IPTV que criamos (37 arquivos, ~3.600 linhas) não existem no upstream e os que modificamos foram deletados/refatorados.

**Decisões já tomadas:**
- Login: adotar AuthManager do upstream (não portar EmailLoginScreen)
- Base: `upstream/dev` (não tag fixa, para acompanhar desenvolvimento)
- SUPABASE_URL/KEY: extrair do APK oficial (já temos a KEY, URL pendente)

---

## FASES DE EXECUÇÃO

### FASE 0 — Preparação (P0)

**0.1** Fazer backup da branch dev-old atual
```
git branch backup/dev-old dev-old
```

**0.2** Criar branch nova a partir de upstream/dev
```
git fetch upstream
git checkout -b migrate/iptv-v0.7.13 upstream/dev
```

**0.3** Push da branch pro origin (CRaulD/NuvioTV-Live)
```
git push -u origin migrate/iptv-v0.7.13
```

**0.4** [PENDENTE] Obter SUPABASE_URL (outro agente trabalhando nisso)
- Anon key já temos
- URL pendente
- Enquanto isso, placeholder vazio pra buildar

---

### FASE 1 — Build Base (P1)

**1.1** Criar `local.properties` com placeholder das creds Supabase
```
NUVIO_SUPABASE_URL=
NUVIO_SUPABASE_ANON_KEY=
```

**1.2** Tentar build do flavor `full` para validar ambiente
```
./gradlew assembleFullDebug
```

**1.3** Corrigir eventuais erros de ambiente (SDK, NDK, JDK)

**1.4** [CHECKPOINT] Build base compilando ✅

---

### FASE 2 — Porte da Camada Core/Domain/Data (P2)

**2.1** Copiar arquivos do `dev-old` pra branch nova:

```
Arquivos fonte (criar como novos — não existem no upstream):
core/iptv/EpgParser.kt
core/iptv/M3uParser.kt
core/di/IptvModule.kt
data/local/IptvDaos.kt
data/local/IptvEntities.kt
domain/model/EpgProgram.kt
domain/repository/IptvRepository.kt
data/repository/IptvRepositoryImpl.kt
```

**2.2** Adaptar `AppDatabase.kt` para incluir IPTV entities:
- Adicionar `IptvEntities` às entities do Room
- Incrementar versão do banco

**2.3** Adaptar `PlayerSettingsDataStore.kt` se necessário (modificamos no fork)

**2.4** [CHECKPOINT] Compilar — corrigir erros de Room/KSP

---

### FASE 3 — Porte dos Servidores QR (P3)

**3.1** Copiar do dev-old:
```
core/server/IptvConfigServer.kt
core/server/IptvWebPage.kt
core/server/TvLoginServer.kt
core/server/TvLoginWebPage.kt
```

**3.2** Adaptar imports e APIs se necessário (NanoHTTPD pode ter mudado de versão)

**3.3** [CHECKPOINT] Compilar servidores

---

### FASE 4 — Porte da UI IPTV (P4)

**4.1** Copiar arquivos do dev-old:
```
ui/screens/iptv/IptvScreen.kt
ui/screens/iptv/IptvPlayerPane.kt
ui/screens/iptv/IptvEpgPane.kt
ui/screens/iptv/IptvSearchBar.kt
ui/screens/iptv/IptvFocusHandler.kt
ui/screens/iptv/IptvMarqueeText.kt
ui/screens/iptv/IptvFooter.kt
ui/screens/iptv/IptvPlayerScreen.kt
ui/screens/iptv/IptvSetupScreen.kt
ui/screens/iptv/IptvViewModel.kt
```

**4.2** Adaptar `NuvioNavHost.kt` para adicionar rotas IPTV
- Adicionar navegação para `IptvScreen`, `IptvSetupScreen`, `IptvPlayerScreen`

**4.3** Compilar — corrigir erros de API:
- `androidx.tv.material3` (ExperimentalTvMaterial3Api)
- Possível diferença de API do Compose TV
- ViewModel/Hilt integração

**4.4** [CHECKPOINT] UI IPTV compilando

---

### FASE 5 — Autenticação/Login (P5)

**5.1** Configurar credenciais Supabase definitivas (quando a URL chegar)

**5.2** Verificar se AuthManager + AuthSignInScreen/AuthQrSignInScreen funcionam:
- O fluxo de login por email do upstream deve funcionar direto
- Abandonar EmailLoginScreen e TvLoginServer/TvLoginWebPage nosso

**5.3** Adaptar IptvSetupScreen se necessário (antes pedia TV login via QR nosso,
    agora pode usar o QR do upstream)

**5.4** [CHECKPOINT] Login funcional

---

### FASE 6 — Ajustes Finais (P6)

**6.1** Aplicar temas tintados:
- `ThemeColors.kt` — já existe em 0.7.13 (não foi deletado)
- Portar Crimson, Emerald, Rose tints

**6.2** Adicionar strings IPTV ao `res/values/strings.xml`

**6.3** Configurar update system:
- `GITHUB_OWNER` = "CRaulD"
- `GITHUB_REPO` = "NuvioTV-Live"
- Em flavor `full`, `AppFeaturePolicy.kt` determina as URLs de update

**6.4** Bump version:
- `versionCode` = sequencial a partir de 1012
- `versionName` = "0.7.13-nuvio-live-1"

**6.5** Remover arquivos obsoletos do fork antigo (EmailLoginScreen, TvLoginServer, etc) — se ainda existirem

---

### FASE 7 — Build Final e Teste (P7)

**7.1** `./gradlew assembleFullDebug`

**7.2** Corrigir eventuais erros finais

**7.3** Gerar APK:
```
app/build/outputs/apk/full/debug/app-full-arm64-v8a-debug.apk
```

**7.4** Instalar no emulador/dispositivo e testar:
- Login via Supabase (email + senha)
- IPTV: M3U/EPG config via QR
- IPTV: navegação D-pad, marquee, EPG
- Navegação geral do app

---

## FORA DE ESCOPO (o que NÃO vamos portar/reescrever)

Features que JÁ VÊM com a base 0.7.13-beta e que NÃO precisamos modificar:

- ~~Portar EmailLoginScreen~~ → usando AuthManager oficial do upstream
- ~~Portar bump version / remove creds~~ → refeito na nova base
- ~~Portar GitHub workflows~~ → upstream já tem os deles, adaptar só GITHUB_REPO

**Features do upstream que já vêm inclusas e não mexemos:**
- Debrid (RealDebrid, Premiumize, Torbox)
- Cloud Library
- Plugin runtime + CloudStream extensions
- DolbyVision / HDR playback
- CI workflows do upstream
- Traduções (strings.xml em outros idiomas)
- Novo sistema de update (UpdatePromptDialog em app/full)

---

## DEPENDÊNCIAS EXTERNAS

| Item | Status | Responsável |
|------|--------|-------------|
| SUPABASE_URL | Pendente | Outro agente |
| SUPABASE_ANON_KEY | Extraída ✓ | Já temos |
| APK oficial | Baixado ✓ | C:\Users\Raul\nuvio-apk\ |
| Código fonte upstream | No repo ✓ | Tag 0.7.13-beta |
| Código fork | No repo ✓ | Branch dev-old |

---

## CRITÉRIOS DE ÊXITO

1. Build compila no flavor `full` com feature IPTV
2. Login via Supabase funcional (email + QR)
3. IPTV funcional: M3U config, EPG, player, D-pad navigation, marquee
4. Update system apontando CRaulD/NuvioTV-Live
5. Temas tintados Crimson/Emerald/Rose preservados