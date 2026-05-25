## MODIFIED Requirements

### Requirement: Processar pagamento com VALUE_LIMIT
O sistema DEVE processar pagamentos via `POST /wallets/{walletId}/payments` utilizando a politica ativa VALUE_LIMIT da carteira. O header `Idempotency-Key` passa a ser obrigatorio. A verificacao de limite, a insercao da linha de consumo, o registro da chave idempotente e a persistencia do pagamento DEVEM ocorrer na mesma transacao.

#### Scenario: Pagamento aprovado dentro do limite diurno
- **GIVEN** uma carteira com politica DEFAULT_VALUE_LIMIT ativa
- **GIVEN** a carteira nao possui consumo no periodo diurno atual
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** uma requisicao `POST /wallets/{walletId}/payments` com `amount: 500.00` e `occurredAt` em horario diurno for enviada
- **THEN** o sistema DEVE retornar `201 Created`
- **AND** o response DEVE conter `paymentId`, `status: "APPROVED"`, `amount` e `occurredAt`

#### Scenario: Pagamento aprovado dentro do limite noturno
- **GIVEN** uma carteira com politica DEFAULT_VALUE_LIMIT ativa
- **GIVEN** a carteira nao possui consumo no periodo noturno atual
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** uma requisicao com `amount: 500.00` e `occurredAt` em horario noturno for enviada
- **THEN** o sistema DEVE retornar `201 Created`
- **AND** o response DEVE conter `status: "APPROVED"`

#### Scenario: Pagamento aprovado dentro do limite de final de semana
- **GIVEN** uma carteira com politica DEFAULT_VALUE_LIMIT ativa
- **GIVEN** a carteira nao possui consumo no final de semana atual
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** uma requisicao com `amount: 500.00` e `occurredAt` em sabado ou domingo for enviada
- **THEN** o sistema DEVE retornar `201 Created`
- **AND** o response DEVE conter `status: "APPROVED"`

#### Scenario: Rejeita amount zero
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** `amount: 0` for enviado
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Rejeita amount negativo
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** `amount: -100.00` for enviado
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Rejeita amount acima de maxPerPayment
- **GIVEN** politica ativa com `maxPerPayment: 1000.00`
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** `amount: 1500.00` for enviado
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Rejeita soma acima do limite diurno
- **GIVEN** politica ativa com `daytimeDailyLimit: 4000.00`
- **GIVEN** a carteira ja consumiu `3500.00` no periodo diurno atual
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** `amount: 600.00` com `occurredAt` diurno for enviado
- **THEN** o sistema DEVE retornar `422 Unprocessable Entity`

#### Scenario: Rejeita soma acima do limite noturno
- **GIVEN** politica ativa com `nighttimeDailyLimit: 1000.00`
- **GIVEN** a carteira ja consumiu `800.00` no periodo noturno atual
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** `amount: 300.00` com `occurredAt` noturno for enviado
- **THEN** o sistema DEVE retornar `422 Unprocessable Entity`

#### Scenario: Rejeita soma acima do limite de final de semana
- **GIVEN** politica ativa com `weekendDailyLimit: 1000.00`
- **GIVEN** a carteira ja consumiu `900.00` no final de semana atual
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** `amount: 200.00` com `occurredAt` em sabado ou domingo for enviado
- **THEN** o sistema DEVE retornar `422 Unprocessable Entity`

#### Scenario: Aceita soma exatamente igual ao limite
- **GIVEN** politica ativa com `daytimeDailyLimit: 4000.00`
- **GIVEN** a carteira ja consumiu `3500.00` no periodo diurno atual
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** `amount: 500.00` com `occurredAt` diurno for enviado
- **THEN** o sistema DEVE retornar `201 Created`

#### Scenario: Reseta limite em novo dia diurno
- **GIVEN** a carteira consumiu `4000.00` no periodo diurno do dia anterior
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** `amount: 500.00` com `occurredAt` diurno do dia seguinte for enviado
- **THEN** o sistema DEVE retornar `201 Created`

#### Scenario: Reseta limite em nova noite
- **GIVEN** a carteira consumiu `1000.00` no periodo noturno da noite anterior
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** `amount: 500.00` com `occurredAt` noturno da noite seguinte for enviado
- **THEN** o sistema DEVE retornar `201 Created`

#### Scenario: Reseta limite em novo dia de final de semana
- **GIVEN** a carteira consumiu `1000.00` no sabado
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** `amount: 500.00` com `occurredAt` no domingo for enviado
- **THEN** o sistema DEVE retornar `201 Created`

#### Scenario: Usa occurredAt para classificar periodo (nao createdAt)
- **GIVEN** uma requisicao com `occurredAt` em horario diurno
- **GIVEN** o `createdAt` do registro e em horario noturno
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** o pagamento for processado
- **THEN** o periodo DEVE ser classificado como `DAYTIME` com base no `occurredAt`
- **AND** o limite diurno DEVE ser aplicado

#### Scenario: Nao mistura consumo de carteiras diferentes
- **GIVEN** duas carteiras com politica DEFAULT_VALUE_LIMIT ativa
- **GIVEN** a primeira carteira consumiu `4000.00` no periodo diurno
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** `amount: 500.00` com `occurredAt` diurno for enviado para a segunda carteira
- **THEN** o sistema DEVE retornar `201 Created`

#### Scenario: Retorna 404 para walletId inexistente
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** uma requisicao para `/wallets/{inexistentId}/payments` for enviada
- **THEN** o sistema DEVE retornar `404 Not Found`

#### Scenario: Retorna 422 para carteira sem politica ativa
- **GIVEN** uma carteira sem politica ativa
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** uma requisicao de pagamento for enviada
- **THEN** o sistema DEVE retornar `422 Unprocessable Entity`

#### Scenario: Body ausente retorna 400
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** uma requisicao `POST /wallets/{walletId}/payments` sem body for enviada
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: amount ausente retorna 400
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** uma requisicao sem `amount` for enviada
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: occurredAt ausente retorna 400
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** uma requisicao sem `occurredAt` for enviada
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Pagamento rejeitado por limite nao persiste como aprovado
- **GIVEN** a carteira ja consumiu todo o limite diurno
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** `amount: 100.00` for enviado e rejeitado com `422`
- **THEN** nenhum pagamento com status `APPROVED` DEVE ser criado para esta requisicao

#### Scenario: Pagamento aprovado e persistido com campos obrigatorios
- **GIVEN** uma requisicao valida com header `Idempotency-Key`
- **WHEN** o pagamento for aprovado
- **THEN** o registro em `payments` DEVE conter `id`, `wallet_id`, `policy_id`, `amount`, `occurred_at`, `period_type`, `period_start`, `status`, `created_at`, `updated_at`

#### Scenario: Consumo de limite considera pagamentos aprovados da mesma carteira, politica e periodo
- **GIVEN** a carteira possui 3 pagamentos aprovados de `1000.00` no periodo diurno com a mesma politica
- **GIVEN** o header `Idempotency-Key` valido
- **WHEN** um quarto pagamento de `1000.00` for enviado
- **THEN** o sistema DEVE consumir o limite e aprovar
- **AND** um quinto pagamento de `1000.00` DEVE ser rejeitado com `422`
