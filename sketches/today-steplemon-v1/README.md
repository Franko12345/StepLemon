# StepLemon — Today screen, polish v1

3 variantes HTML da tela "Hoje" — referência visual pra Franco escolher direção antes de eu portar pra Android Views (XML).

**Importante:** estes NÃO são o app. São simulações em viewport mobile (390×844, iPhone-ish) usando o vocabulário visual de design systems reais (Linear/Raycast/Notion). Quando Franco escolher, eu traduzo pra `fragment_today.xml` + drawables.

## Variantes

| # | Stance | Inspiração | Arquivo |
|---|--------|-----------|---------|
| 1 | **Editorial airy** | Notion + Linear, generoso whitespace, tipografia dita a hierarquia | `01-editorial-airy.html` |
| 2 | **Compact dense** | Raycast, info-rich, valores sempre visíveis | `02-compact-dense.html` |
| 3 | **Hero centered** | Apple Health, donut gigante no centro, decisões mínimas | `03-hero-centered.html` |

## Como abrir

```bash
xdg-open sketches/01-editorial-airy.html    # Linux
# ou abrir manualmente os 3 lado a lado em 3 abas
```

## Restrições respeitadas (mantidas ao portar pra Android)

- Paleta 🍋 mantida: `#0E1411` bg, `#9CCC65` primary lime, `#D4E157` secondary yellow, `#F472B6` pink.
- Sem third-party deps — fonte via Google Fonts (Inter, DM Sans) só pra visualização.
- Dark mode only.
- Estrutura replica o que tem no `fragment_today.xml`: pill de fonte, donut, 3 goal cards, 3 stat cards.

## O que NÃO mudou (propositalmente)

- 3 anéis = 3 metas (mantido, é a alma do app).
- Icons emoji-style: 🔥 streak, 📏 distance, 🏢 floors (mantidos iguais ao app atual).
- 22pt bold nos valores, 11sp dim nas labels (consistente com app atual).

## Decisões de design que variam entre variantes

| Dimensão | Editorial | Compact | Hero |
|---|---|---|---|
| Donut size | 200dp | 200dp | 280dp |
| Spacing entre cards | 24dp | 12dp | 32dp |
| Tipografia do nº de passos | 56sp | 44sp | 72sp |
| Pill de fonte | canto sup. dir. | inline acima do nº | canto sup. dir. |
| Stat cards labels | "Sequência" / "Distância" / "Calorias" | ícone + valor, label embaixo | ícone + label + valor |
| Hint "zepp required" | box inteiro dedicado | inline discreto | não mostra se tiver Zepp |