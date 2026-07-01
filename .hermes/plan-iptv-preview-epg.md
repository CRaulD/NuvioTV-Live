# Plano: Preview + EPG lateral

## Objetivo
Evoluir o layout da tela **TV ao Vivo** de lista única para **dois painéis**:
**sidebar + lista de canais (esq) + preview/EPG (dir)**, estilo TiviMate.

## Prioridades

### P0 — Preview passivo (painel informativo)
- Ao focar um canal na lista, o painel direito mostra:
  - Nome do canal (grande)
  - Programa atual (legenda)
  - Indicador "🔴 Ao vivo" com blink (se `isLive`)
  - Logo do canal
- Grade EPG: lista dos programas do dia
  - Programa atual destacado com barra de progresso
  - "X% · Y min restantes" no atual
  - Duração nos demais
- Painel **navegação passiva** — atualiza conforme foco na lista

### P1 — Navegação entre painéis
- `→` da lista vai pro painel direito
- No painel direito, `↑↓` navega pelos itens da grade EPG
- `←` volta pra lista de canais
- Scroll acompanha o item focado

### P2 — Adaptações visuais
- Favoritar pelo painel direito (★ grande no preview)
- Badge HD quando disponível
- Animação de transição suave entre canais no preview

## Fora de escopo (agora)
- Player de vídeo no preview (só imagem estática)
- Multi-seleção de canais
- Drag & drop na lista
- Busca inline no mesmo layout (será P3 separada)

## Riscos
- **Largura da lista**: Em telas 720p (Intelbras), o painel direito reduz a lista ~340px. Se ficar apertado, podemos:
  - Fazer o painel direito ocupar menos espaço
  - Ou deixar opcional (toggle com botão)
- **RAM**: EPG carregado de uma vez pode ser pesado. Solução: paginar se necessário, mas com Room + StateFlow deve ser suave

## Próximo passo
Implementar **P0 — Preview passivo**.
