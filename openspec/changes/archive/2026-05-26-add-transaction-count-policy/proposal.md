# Proposal: Adicionar Politica TX_COUNT_LIMIT

## Why

O desafio backend-challenge exige que o sistema demonstre extensibilidade de politicas de limite. Alem da politica padrao `VALUE_LIMIT`, e necessario suportar uma categoria alternativa `TX_COUNT_LIMIT` que restrinja a quantidade diaria de transacoes aprovadas, sem alterar o fluxo principal de pagamento.

## What Changes

- Adicionar suporte a categoria de politica `TX_COUNT_LIMIT`.
- Permitir criacao de politicas `TX_COUNT_LIMIT` via `POST /policies`.
- Implementar avaliador `TxCountLimitEvaluator` registrado no `PolicyEvaluatorRegistry`.
- Reutilizar a tabela `policies` com campo `daily_transaction_limit`.
- Reutilizar `limit_consumptions.transaction_count` para controle concorrente de quantidade de transacoes.
- Garantir que o fluxo de pagamento em `POST /wallets/{walletId}/payments` continue generico, delegando avaliacao ao avaliador correspondente.
- Adicionar testes unitarios e de integracao para a nova categoria.

## Capabilities

### New Capabilities

- `transaction-count-policy`: Especifica o comportamento da politica `TX_COUNT_LIMIT`, incluindo criacao, validacao, avaliacao no pagamento e controle de limite diario por quantidade de transacoes.

### Modified Capabilities

- `policies`: O endpoint `POST /policies` deve aceitar a nova categoria `TX_COUNT_LIMIT` com campo `dailyTransactionLimit`. O endpoint `GET /policies` e `GET /wallets/{walletId}/policies` devem retornar a estrutura especifica da categoria.
- `payments`: O processamento de pagamento deve delegar avaliacao ao `PolicyEvaluatorRegistry`, incluindo o novo avaliador, sem mudancas no contrato HTTP.

## Impact

- Camada de dominio: novo avaliador `TxCountLimitEvaluator` e ajustes no `PolicyEvaluatorRegistry`.
- Camada de aplicacao: uso do registry para resolver avaliador dinamicamente.
- Camada de infraestrutura/persistencia: campo `daily_transaction_limit` na tabela `policies` e uso de `transaction_count` em `limit_consumptions`.
- APIs: `POST /policies` e respostas de politicas ganham novo formato para `TX_COUNT_LIMIT`.
- Testes: novos testes de unidade e integracao.
