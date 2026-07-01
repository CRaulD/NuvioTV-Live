# Plano: Redesign Completo IPTV (Opção A)

> **Modo Planejamento** — sem execução até aprovação

## Objetivo

Transformar a tela **TV ao Vivo** no layout exato da demo HTML:
```
┌──────────────┬──────────────────┬─────────────────────────────┐
│ Categorias   │  Lista canais     │  Player 16:9                │
│ (como está)  │  Logo 56dp        │  ● AO VIVO • gradient      │
│              │  ★ Unicode        │  Logo + info + progresso    │
│ Todos        │  hover + opac.    ├─────────────────────────────┤
│ HBO          │  borda vermelha   │  Programação                │
│ Infantil     │  progresso        │  ▶ Atual · AO VIVO          │
│ PPV Dazn     │                   │  Próximos · 60min           │
└──────────────┴──────────────────┴─────────────────────────────┘

> **✅ Decidido:** NavIcon **não cabe** — layout final é **3 colunas** (Categories + ChannelList + Player/EPG)
```

## Cores e tema

> **⚠️ Importante:** As cores do mockup (preto `#0e0e0e`, vermelho `#e50914`) são **apenas referência visual**. O layout e a estrutura devem seguir o mockup fielmente, mas as **cores e o tema respeitarão o NuvioTheme já definido no app**, com a paleta `NuvioTheme.colors`. Não vamos criar um tema novo nem substituir cores — o visual do mockup serve pra guiar a *estrutura*, não a paleta.

| O que segue o mockup | O que NÃO segue |
|---|---|
| Layout 3-4 colunas (ícones? \| categorias \| lista \| player+EPG) | Cores exatas do mockup |
| Tamanhos relativos (256dp, 400dp) | Fontes (manter a atual) |
| Comportamento: hover, gradiente no selecionado, AO VIVO pulsando | Iconografia (manter Unicode ★☆● que funciona na TV) |
| Overlays do player (AO VIVO, progresso, logo) | Backdrop blur (se não tiver suporte) |

## Arquivos que serão modificados

| Arquivo | Mudança |
|---|---|
| `IptvScreen.kt` | Reescrever layout (3-4 colunas) + NavIcon + Card + Sidebar |
| `IptvViewModel.kt` | Adicionar toggle mini player + estado |
| `PlayerSettings.kt` | Adicionar `miniPlayerEnabled` boolean |
| `PlayerSettingsDataStore.kt` | Key + save/load do toggle |
| **Novo:** `IptvPlayerPane.kt` | Componente Mini ExoPlayer + overlays |
| **Novo:** `IptvEpgPane.kt` | Grade EPG abaixo do player |

| Task | Status |
|---|---|
| 0 — NavIconSidebar | ❌ Reprovada |
| 1 — CategorySidebar | ✅ Concluída |
| 2 — ChannelCard redesign | ✅ Concluída |
| 3 — Mini ExoPlayer | ✅ Concluída |
| 4 — EPG Pane | ✅ Concluída |
| 5 — Toggle mini player | ⏳ Pendente |
| 6 — Layout principal | ✅ Concluída |
| 7 — DataStore crash fix | ✅ Concluída |
| 8 — Email Login Screen | ✅ Concluída |

## Próximos passos (próxima sessão)

| Task | Descrição |
|---|---|
| 5 — Toggle mini player | `miniPlayerEnabled` no DataStore |
| 9 — Investigar navegação Home → IPTV | Pode ser efeito das alterações no MainActivity |

## Riscos e tradeoffs

| Risco | Impacto | Mitigação |
|---|---|---|
| ExoPlayer no preview consome RAM no Intelbras | App lento ou crash | Toggle pra desativar (Task 5) |
| 3-4 colunas em 720p pode apertar | Conteúdo comprimido ou scroll horizontal | NavIcon colapsável ou removível |
| Mudança grande no layout | Regressões de navegação D-pad | Testar foco entre as colunas |
| ExoPlayer rodando enquanto navega | Buffer desnecessário | Pausar player quando não focado |

## Fora de escopo AGORA

- ❌ **PlayerSettings DataStore crash** (já sabemos contornar com profile Teste 2)
- ❌ ExoPlayer real reproduzindo no clique do card (já funciona com StreamingPlayer)
- ❌ Busca inline (P3 separada)
- ❌ Multi-áudio / legendas no mini player

## Critérios de sucesso

- [ ] Layout fiel ao demo em 1080p (largura suficiente)
- [ ] Navegação D-pad entre as colunas funciona
- [ ] Mini player mostra stream ao vivo do canal selecionado
- [ ] Toggle desliga player e volta pro preview estático
- [ ] EPG lista programas corretamente com destaque no atual
- [ ] Intelbras 1GB RAM não crasha (toggle desligado se necessário)
