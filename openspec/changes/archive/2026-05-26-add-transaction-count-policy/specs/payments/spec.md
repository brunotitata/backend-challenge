## ADDED Requirements

### Requirement: Processar pagamento com TX_COUNT_LIMIT
O sistema MUST processar pagamentos via `POST /wallets/{walletId}/payments` utilizando a politica ativa `TX_COUNT_LIMIT` da carteira, contando transacoes aprovadas no dia e rejeitando quando `dailyTransactionLimit` for atingido.

#### Scenario: Pagamento aprovado com TX_COUNT_LIMIT dentro do limite
- **GIVEN** uma carteira com politica `TX_COUNT_LIMIT` ativa com `dailyTransactionLimit: 5`
- **GIVEN** a carteira possui 4 pagamentos aprovados no dia atual
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** uma requisicao `POST /wallets/{walletId}/payments` com `amount: 10.00` e `occurredAt` no mesmo dia for enviada
- **THEN** o sistema MUST retornar `201 Created`
- **AND** o response MUST conter `paymentId`, `status: "APPROVED"`, `amount` e `occurredAt`

#### Scenario: Pagamento rejeitado com TX_COUNT_LIMIT acima do limite
- **GIVEN** uma carteira com politica `TX_COUNT_LIMIT` ativa com `dailyTransactionLimit: 5`
- **GIVEN** a carteira ja possui 5 pagamentos aprovados no dia atual
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** uma requisicao `POST /wallets/{walletId}/payments` com `amount: 10.00` e `occurredAt` no mesmo dia for enviada
- **THEN** o sistema MUST retornar `422 Unprocessable Entity`

#### Scenario: Reseta contador TX_COUNT_LIMIT no dia seguinte
- **GIVEN** uma carteira com politica `TX_COUNT_LIMIT` ativa com `dailyTransactionLimit: 5`
- **GIVEN** a carteira possui 5 pagamentos aprovados no dia anterior
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** uma requisicao com `occurredAt` no dia seguinte for enviada
- **THEN** o sistema MUST retornar `201 Created`

#### Scenario: Concorrencia nao permite aprovar mais transacoes que dailyTransactionLimit
- **GIVEN** uma carteira com politica `TX_COUNT_LIMIT` ativa com `dailyTransactionLimit: 2`
- **GIVEN** a carteira nao possui pagamentos aprovados no dia atual
- **GIVEN** o header `Idempotency-Key` valido e unico para cada requisicao
- **WHEN** tres requisicoes simultaneas de pagamento forem enviadas
- **THEN** exatamente duas MUSTm retornar `201 Created`
- **AND** exatamente uma MUST retornar `422 Unprocessable Entity`

#### Scenario: TX_COUNT_LIMIT nao depende do valor do pagamento para aprovar
- **GIVEN** uma carteira com politica `TX_COUNT_LIMIT` ativa com `dailyTransactionLimit: 2`
- **GIVEN** a carteira possui 1 pagamento aprovado no dia atual
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** uma requisicao com `amount: 999999.99` for enviada
- **THEN** o sistema MUST retornar `201 Created` com `status: "APPROVED"`, pois o limite e por quantidade e nao por valor
