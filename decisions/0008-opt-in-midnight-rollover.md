# ADR 0008: Opt-in midnight rollover (alarme diário, sem background service)

- **Status:** Accepted
- **Date:** 2026-08-24
- **Decider:** Franco Valois Delucca
- **Supersedes (parcialmente):** [ADR 0002](./0002-no-background-service.md) — não revoga, adiciona
  uma exceção explícita: 1 alarme exato por dia, opt-in, sem notificação.

## Contexto

Franco reportou (ticket informal, conversa):

> "gostaria que ocasionalmente ele atualizasse sem necessidade de abrir o aplicativo
> pois nem sempre eu quero abrir o aplicativo mas eu sempre quero que os passos
> estejam sendo contabilizados pelo menos logo antes da meia-noite para não perder
> os dados do dia anterior"

> "Mas pode ter um botão também de ativar o desativar o serviço em background para
> garantir a privacidade dos usuários que não querem serviços no background do
> dispositivo deles."

E em outro momento: "o histórico das estatísticas não conta os dias anteriores, quando
acaba o dia as estatísticas não são guardadas. Acredito que não está sendo armazenado
os devidos valores."

Problema técnico diagnosticado: o `StepRepository.readMergedHistory()` lê apenas Zepp
(que é histórico e persistente) + sensor nativo (que é "desde a abertura do app"). Se
o usuário NÃO tem Zepp autorizado e NÃO abre o app no fim do dia, a meia-noite
chega e o `rollMidnightIfNeeded()` redefine `midnightRawTotal` para o próximo valor
do sensor — apagando o delta do dia anterior. Resultado: dias passados somem.

ADR 0002 proibia serviços em background. Mas a v1.3 + este ticket mostram que **um
único alarme diário às 23:55**, sem foreground service e sem notificação persistente,
atende ao requisito sem custar bateria — e mantém a postura de privacidade (opt-in,
default OFF).

## Decisão

Adicionar `MidnightRolloverReceiver` (BroadcastReceiver) agendado via
`AlarmManager.setExactAndAllowWhileIdle` para a janela 23:55–00:00 (jitter +5min).
Opt-in via Switch em SettingsFragment. Default OFF.

### Fluxo do receiver

1. Lê Zepp (se instalado/autorizado) — `StepRepository.readZeppHistory(7)`.
2. Lê sensor nativo — `StepRepository.readNativeStepsToday()`.
3. Persiste cada par (data → passos) em SharedPreferences `"stepwatch_history"`
   com chave `"d_<yyyy-MM-dd>"` e fonte ("zepp" / "sensor").
4. Re-agenda o próximo alarme (idempotente).
5. NÃO mostra notificação. NÃO inicia foreground service. NÃO faz loop.

### `readMergedHistory` ganhou 3ª camada

Ordem de merge:
1. Zepp (se instalado/autorizado e retorna > 0)
2. Histórico local `"stepwatch_history"` (persistido pelo receiver — cobre dias
   passados mesmo sem Zepp)
3. Sensor nativo (apenas para hoje — ephemeral)

### Permissões adicionadas

- `SCHEDULE_EXACT_ALARM` — Android 12+. Cai pra `setWindow` 10min se não granted.
- `USE_EXACT_ALARM` — Android 13+. Declarada para fallback automático.
- `RECEIVE_BOOT_COMPLETED` — já existia. `BootReceiver` agora re-agenda o alarme
  se a flag estiver ON (antes era no-op).

### Comportamento por estado do app

| Estado | Comportamento |
|--------|---------------|
| Switch OFF (default) | Sem alarme agendado, sem código rodando, zero custo. |
| Switch ON, app fechado | Alarme dispara às 23:55±5min, persiste, re-agenda. |
| Switch ON, app aberto | Comportamento idêntico — receiver ainda roda (é idempotente). |
| Boot do dispositivo | `BootReceiver` re-agenda se flag ON. |
| Zepp ON + sensor ON | Zepp ganha (histórico mais confiável); sensor é fallback. |
| Zepp OFF | Sensor nativo é a única fonte ativa. |

### Compatibilidade MIUI/HyperOS

- `setExactAndAllowWhileIdle` (não `setExact`) sobrevive ao Doze mode.
- Sem foreground service → MIUI não mata por "serviço não usado".
- `SCHEDULE_EXACT_ALARM` cai pra `setWindow` se o usuário não concedeu.
- Jitter 0-5min reduz chance de todos os devices acordarem ao mesmo tempo.

## Trade-offs

**Positivo**
- Atende ao pedido literal do Franco: passos do dia não somem mais (mesmo se Zepp não autorizado).
- Custo de bateria mínimo: 1 wakeup/dia, trabalho < 100ms (1 query ContentResolver).
- Postura de privacidade preservada: default OFF, sem notificação, sem service contínuo.
- Idempotente em todo lugar (MainActivity.onCreate, BootReceiver, receiver).
- Sem novas deps (só `AlarmManager`, `BroadcastReceiver` do stdlib Android).

**Negativo**
- MIUI pode atrasar o alarme em alguns minutos (Doze agressivo). Aceitável — janela 23:55–00:00 ainda captura o dia.
- Se o usuário desligar o switch, dados passados persistidos continuam no SharedPreferences
  (não limpamos). É por design — apagar histórico sem consentimento é pior que manter.
- Requer `SCHEDULE_EXACT_ALARM` (Android 12+). Cai pra janela sem grant — funciona, mas
  impreciso. Documentado em UI.
- `USE_EXACT_ALARM` no Android 13+ exige que o app seja "alarmes ou calendários". StepLemon
  tecnicamente é — agenda 1 alarme diário. Play Store review pode pedir justificativa.
  Alternativa se rejeitado: remover `USE_EXACT_ALARM` e ficar só com `SCHEDULE_EXACT_ALARM`.

**Edge cases conhecidos**
- Se o alarme dispara exatamente em 00:00:00.001 (após jitter máximo de 23:55 + 5min),
  `todayDate()` retorna o dia novo e persistimos sob a data errada. Aceitável: Zepp
  ainda tem o dia anterior via `readZeppHistory`.
- Se o usuário ativa o switch e o alarme está em <5min de distância, `nextTriggerMillis()`
  pula para amanhã. Primeira captura acontece no dia seguinte. Documentado.
- BootReceiver só re-agenda se flag ON. Se OFF, no-op (como antes).

## Privacidade (reforço)

- Nenhuma chamada de rede no receiver. Só ContentResolver Zepp + SharedPreferences local.
- Nenhum dado sai do device.
- Default OFF: usuário precisa explicitamente ligar.
- Switch tem descrição PT-BR explicando o que faz.
- `stepwatch_rollover` SharedPreferences contém apenas `enabled: Boolean`.
- `stepwatch_history` contém apenas `<data>: Long` + `<data>_source: String` ("zepp"/"sensor").
- Nada disso é copiado para backup (`android:allowBackup="true"` mas o conteúdo é
  regenerável — passos não são confidenciais).

## Como testar

### Unit (já adicionado)

- `StepRepositoryTest.rolover_save_persists_to_history_prefs` — mock SharedPreferences,
  verifica que `saveHistoryEntry` escreve a chave `d_<date>` e `s_<date>`.
- `StepRepositoryTest.rollover_cancel_calls_alarm_manager_cancel` — mock AlarmManager +
  PendingIntent, verifica `cancel(pi)` + `pi.cancel()`.
- `StepRepositoryTest.rollover_cancel_null_pi_is_noop` — quando não há alarme agendado,
  `cancel` é no-op (não chama `am.cancel`).

### Device (manual)

1. Instalar APK da branch `feat/background-rollover-optin`.
2. Abrir app, ir em Ajustes, ligar o switch "Salvar passos automaticamente à meia-noite".
3. Verificar toast/texto: "Alarme agendado".
4. `adb shell dumpsys alarm | grep com.stepwatch.app` → deve aparecer 1 alarme RTC_WAKEUP.
5. Esperar até 23:55 (ou mudar hora do device pra 23:54 e esperar).
6. `adb logcat -s StepWatch:V` → deve mostrar `MidnightRolloverReceiver: done`.
7. Verificar SharedPreferences: `adb shell run-as com.stepwatch.app cat shared_prefs/stepwatch_history.xml`
   deve ter entradas `d_<data>`.
8. Desligar switch → `dumpsys alarm` deve mostrar 0 alarmes.
9. Reiniciar device → `BootReceiver` deve re-agendar se flag ainda ON.

### Doze / MIUI

- `adb shell dumpsys deviceidle force-active` → depois `force-inactive` e ver se alarme dispara.
- `adb shell cmd appops set com.stepwatch.app RUN_IN_BACKGROUND allow` para MIUI.

## Quando revisitar

- Se Play Store rejeitar por causa de `USE_EXACT_ALARM`, remover essa permissão
  e depender só de `SCHEDULE_EXACT_ALARM` (com prompt de grant no primeiro ON).
- Se quisermos adicionar notificação "meta batida" (spec v3), aí sim faz sentido
  um WorkManager one-shot — essa feature é a única do ADR 0002 que justifica
  quebrar a regra.
- Se Zepp confirmar o schema (ticket 06 fechado) e for 100% confiável, podemos
  remover a camada de sensor nativo no merge (mas manter `stepwatch_history` como
  belt-and-suspenders).
- Se MIUI começar a atrasar o alarme consistentemente >30min, considerar adicionar
  WorkManager periódico de 1h entre 22:00-00:00 com fallback ao alarme exato.
