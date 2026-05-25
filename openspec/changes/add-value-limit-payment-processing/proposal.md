## Why

Processar pagamentos usando a politica ativa VALUE_LIMIT associada a carteira. Sem esta capacidade, o sistema aceita carteiras e politicas mas nao e capaz de processar transacoes financeiras respeitando limites por periodo (diurno, noturno e final de semana) e valor maximo por pagamento.

## What Changes

- Novo endpoint `POST /wallets/{walletId}/payments` para processar pagamentos.
- Avaliacao da politica ativa `VALUE_LIMIT` em tempo de execucao via `PolicyResolver` e `ValueLimitEvaluator`.
- Classificacao de periodo (`DAYTIME`, `NIGHTTIME`, `WEEKEND`) a partir do `occurredAt`.
- Calculo de consumo acumulado por carteira, politica, periodo e chave de periodo.
- Rejeicao de pagamentos que excedam `maxPerPayment`, limite diurno, noturno ou de final de semana.
- Persistencia de pagamentos aprovados na tabela `payments`.
- Tabela `limit_consumptions` para controle atomico de consumo.

## Capabilities

### New Capabilities
- `payments`: processamento de pagamentos com validacao de carteira, resolucao de politica ativa e persistencia do resultado
- `value-limit-policy`: avaliador especifico para politicas `VALUE_LIMIT` que verifica `maxPerPayment` e limites por periodo

### Modified Capabilities
- _(nenhuma)_

## Impact

- Nova rota `POST /wallets/{walletId}/payments`
- Novas tabelas: `payments` e `limit_consumptions`
- Novo avaliador: `ValueLimitEvaluator` registrado no `PolicyEvaluatorRegistry`
- Reuso da classificacao de periodo da Fatia 3 (`add-payment-period-classification`)
- Dependencia da politica ativa resolvida em tempo de execucao
