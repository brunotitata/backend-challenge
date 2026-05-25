## Context

O sistema ja possui carteiras, politicas VALUE_LIMIT e classificacao de periodos (DAYTIME, NIGHTTIME, WEEKEND). Falta a capacidade de processar pagamentos que consumam limite da politica ativa. O processamento deve resolver a politica ativa da carteira, classificar o periodo, verificar limites e persistir o resultado.

## Goals / Non-Goals

**Goals:**
- Implementar `POST /wallets/{walletId}/payments` com validacao de carteira, politica ativa, periodo e limites.
- Implementar `ValueLimitEvaluator` para avaliar `maxPerPayment` e limites por periodo.
- Persistir pagamentos aprovados em `payments` e consumo em `limit_consumptions`.
- Rejeitar pagamentos que excedam qualquer limite com `422 Unprocessable Entity`.

**Non-Goals:**
- Idempotencia completa (sera tratada na Fatia 5).
- Listagem de pagamentos (Fatia 7).
- TX_COUNT_LIMIT (Fatia 8).

## Decisions

1. **Tabela `limit_consumptions` para consumo agregado**: Em vez de calcular consumo somando `payments` a cada requisicao, manter uma tabela de consumo agregado por `(wallet_id, policy_id, period_type, period_start)`. Isso evita scans repetidos e prepara o terreno para concorrencia com UPSERT na Fatia 6.

2. **Classificacao de periodo reaproveitada**: O dominio de classificacao de periodo da Fatia 3 sera usado diretamente. O `occurredAt` informado no pagamento define o periodo e o `period_start`.

3. **ValueLimitEvaluator registrado no PolicyEvaluatorRegistry**: Seguindo o design de extensibilidade, o avaliador especifico para `VALUE_LIMIT` implementa `PolicyEvaluator` e e registrado no registry. O fluxo de pagamento resolve a politica, obtem o avaliador e delega a avaliacao.

4. **Limites inclusivos**: Valores exatamente iguais ao limite restante sao aprovados.

5. **Periodo `NIGHTTIME` usa chave de noite cruzada**: Para `occurredAt` entre `00:00` e `06:00`, o `period_start` e o dia anterior as `18:00`. Para `occurredAt` entre `18:00` e `23:59`, o `period_start` e o mesmo dia as `18:00`. Isso permite que pagamentos da mesma noite compartilhem o mesmo consumo.

6. **Timezone America/Sao_Paulo**: Conforme definido na Fatia 3, a classificacao de periodo usa `America/Sao_Paulo` (UTC-3). O `occurredAt` e recebido em UTC e convertido para o timezone operacional.

7. **Transacao atomica com SELECT FOR UPDATE**: Pagamento e consumo sao persistidos na mesma transacao jOOQ usando `dsl.transaction { ... }`. O `SELECT ... FOR UPDATE` na linha `limit_consumptions` serializa requisicoes concorrentes para a mesma chave de `(wallet_id, policy_id, period_type, period_start)`. Se a linha ainda nao existir, o INSERT e feito sem lock previo, com a constraint UNIQUE garantindo unicidade. Isso elimina os dois riscos de concorrencia e dessincronizacao.

## Risks / Trade-offs

- **Lock granular por periodo**: Requisicoes para periodos diferentes da mesma carteira (ex: diurno e noturno do mesmo dia) nao competem entre si. Requisicoes para o mesmo periodo serializam. Comportamento desejado.
- **Deadlock potencial**: Se dois locks forem adquiridos em ordem diferente, pode ocorrer deadlock. Mitigacao: adquirir locks sempre na mesma ordem (por `period_type` + `period_start`). Para esta fatia com um unico lock por transacao, o risco e teorico.
- **Performance do SELECT FOR UPDATE**: Lock mantido ate o COMMIT da transacao. Para o volume esperado do desafio, o impacto e irrelevante.
