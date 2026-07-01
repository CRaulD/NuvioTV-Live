# NuvioTV-Live — IPTV: TV ao Vivo

## Contexto do Projeto

Fork do NuvioTV focado no **Intelbras TV Stick Full HD** (1GB RAM, Amlogic S905Y2, Android TV 10/11). O objetivo é ter uma interface leve e rápida pra TV Box, inspirada no TiviMate.

---

## O que já foi implementado

### P0 — Tela base de IPTV ("TV ao Vivo")
- **Sidebar** com categorias (grupos do M3U)
- **Lista de canais** com LazyColumn
- **Card de canal** com número, logo, nome, programa atual, barra de progresso
- **Ao clicar** no card → abre o player (navega pra StreamingPlayer)
- **Header** com botões "Atualizar" e "Configurar" (sem faixa de fundo)
- **Tela de setup** (`IptvSetupScreen`) para inserir URLs do M3U e EPG

### P1 — EPG (Programação atual)
- `EpgProgram` model com `startTime`, `endTime`, `title`, `description`
- `isNow` pra detectar programa atual
- Barra de progresso no card
- `refreshCurrentPrograms()` carrega programa atual de cada canal
- `startProgramWatcher()` atualiza a cada 30s

### P2 — Favoritos
- `FavoritesActivityDao` com tabela `favorites` (channelId)
- `toggleFavorite()` no repositório
- **Categoria "⭐ Favoritos"** no topo da sidebar (se houver favoritos)
- ★ **Estrela clicável** no card (navegável por D-pad)
- `startFavoriteWatcher()` atualiza a cada 5s
- `_updateTick` pra forçar recombine quando dados mudam

### P0 — Redesign visual (fiel ao mockup HTML)

#### CategorySidebar
- Título "CANAIS" em uppercase tracking-wide
- Selecionado: barra vermelha (3dp) na lateral + fundo sutil
- Não selecionado: texto com 50% opacidade
- Sem card colorido no fundo

#### ChannelCard
- Logo 52dp (vs 36dp antes)
- ★ Unicode 18sp (vs 16sp antes)
- Borda vermelha `Primary/40` no foco
- Grade fundo sutil no foco
- Progresso integrado inline (nome → programa → barra → horário)
- Overlay invisível pro clique (sem Card aninhado)

#### Mini ExoPlayer (`IptvPlayerPane.kt`)
- Player real 16:9 com ExoPlayer + `AndroidView` + `PlayerView`
- Gradient overlay: transparente → preto 75%
- ● AO VIVO pulsando (canto superior esquerdo) com animação `infiniteRepeatable`
- Logo do canal (canto inferior esquerdo, h=36dp)
- Nome do programa + progresso + horas início/fim + "X min restantes"
- Toca o stream do canal focado (via `focusedChannelId` no UiState)

#### EPG Schedule (`IptvEpgPane.kt`)
- Card com borda e fundo sutil
- Título "PROGRAMAÇÃO" uppercase
- Programa atual com badge "AO VIVO"
- Próximos programas com duração
- Footer "📅 Ver programação completa" com ícone ❯
- Barra de progresso no programa atual

#### Fluxo de dados
- `focusedChannelId` exposto no `IptvUiState` (antes estava privado no ViewModel)
- Player e EPG reagem automaticamente ao foco na lista de canais

### 🛠️ DataStore crash — CORRIGIDO definitivamente
- `PlayerSettingsDataStore` crashava ao ler chaves legadas `nextEpisodeThresholdPercent` e `nextEpisodeThresholdMinutesBeforeEnd` com tipo incompatível (Int vs Float)
- **Fix:** `runCatching{}` nas leituras, migration que limpa chaves antigas, `.catch{}` no Flow `playerSettings` que emite defaults em vez de crashar
- Antes era contornado deletando `player_settings.preferences_pb`

### 🔐 Tela de Login Email + Senha (`EmailLoginScreen.kt`)
- Substituiu `AuthQrSignInScreen` (QR Code quebrado — dependia de servidor Nuvio)
- Email + Senha + Entrar + Criar Conta + Pular
- Usa `AuthManager.signInWithEmail()` e `signUpWithEmail()` já existentes via Supabase
- Supabase configurado em `local.dev.properties` (mesma conta original)
- Bloqueia o app até login ou "Pular"

### 📁 Arquivos modificados/criados (sessão atual)

| Arquivo | Mudança |
|---|---|
| **Novo:** `EmailLoginScreen.kt` | Tela de login email+senha |
| `MainActivity.kt` | Substitui AuthQrScreen por EmailLoginScreen |
| `PlayerSettingsDataStore.kt` | `runCatching{}` + `.catch{}` no playerSettings Flow |
| `IptvScreen.kt` | CategorySidebar + ChannelCard + Header + layout 3 colunas |
| `IptvViewModel.kt` | `focusedChannelId` no UiState |
| **Novo:** `IptvPlayerPane.kt` | Mini ExoPlayer 16:9 com overlays |
| **Novo:** `IptvEpgPane.kt` | Grade EPG card com badge AO VIVO |
| **Removido:** PreviewPanel | Substituído pelo player + EPG |

### ❌ NavIcon testada e rejeitada
- Barra de ícones (🏠 🔍 📥 📺 ⚙️) testada no 720p
- Resultado: **apertou o layout** — removida
- Layout final: **3 colunas** (Categories + ChannelList + Player/EPG)

---

## Planos futuros

### P1 — Navegação entre painéis
- `→` da lista vai pro painel direito (grade EPG)
- `↑↓` navega pelos programas na grade
- `←` volta pra lista de canais
- Scroll sincronizado com o item focado

### P2 — Ajustes visuais
- ★ Favoritar também pelo preview
- Badge HD no card/preview
- Transição suave entre canais no preview
- Indicador "ao vivo" com blink mais visível

### P3 — Busca
- Campo pra filtrar canais por nome
- Inline na topbar ou dialog com teclado virtual

### P5 — Navegação Home → IPTV
- Investigar por que a navegação pelo drawer lateral parou de funcionar
- Pode ser efeito das mudanças no MainActivity (login, etc.)
- Testar em hardware real (Intelbras) antes de debugar no emulador

### P6 — Toggle desligar Mini ExoPlayer
- `miniPlayerEnabled` no DataStore
- Se desligado, preview estático

### Fora de escopo (agora)
- Player de vídeo no preview (só imagem estática)
- Multi-seleção de canais
- Drag & drop na lista

---

## Riscos conhecidos

| Risco | Impacto | Mitigação |
|---|---|---|
| `PlayerSettingsDataStore` crash (Integer→Float) | App fecha ao iniciar perfil 1 | Usar perfil Teste 2; deletar `player_settings.preferences_pb` |
| `BasicTextField` sem foco no Android TV | Setup não navegável por D-pad | Adicionar `.focusTarget()` nos campos |
| Dois painéis + sidebar em 720p (Intelbras) | Layout apertado | Reduzir sidebar pra 186px; toggle pra esconder preview |
| Room identity_hash | DB recriado ao modificar schema | Usar migração Room ou inserir hash correto |

## Stack técnica

| Camada | Tecnologia |
|---|---|
| UI | Jetpack Compose + `androidx.tv.material3` |
| ViewModel | `IptvViewModel` com StateFlow + `combine` |
| Banco | Room (nuvio_iptv.db) + DataStore (perfil) |
| DI | Hilt |
| EPG | XmlPullParser → Room |
| M3U | Regex parser → Room |
| Player | ExoPlayer (NuvioTV) |

## Convenções de código

- **Unicode pra TV**: usar caracteres Unicode (★☆●) em vez de ícones/imagens
- **Cores**: `NuvioTheme.colors`
- **Navegação D-pad**: `focusTarget()` pra campos editáveis, `Card(onClick=)` pra elementos focáveis
- **Atualizações**: watchers com `delay()` + `_updateTick` pra forçar recomposição
