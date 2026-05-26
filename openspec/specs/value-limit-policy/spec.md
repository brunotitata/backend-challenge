## Purpose
Especifica requisitos funcionais e de comportamento para esta capability.n

## Requirements

### Requirement: ValueLimitEvaluator avalia politica VALUE_LIMIT
O sistema MUST conter um `ValueLimitEvaluator` que implementa `PolicyEvaluator` e avalia politicas de categoria `VALUE_LIMIT`.

#### Scenario: Evaluator registrado para categoria VALUE_LIMIT
- **GIVEN** o `PolicyEvaluatorRegistry`
- **WHEN** consultado por categoria `VALUE_LIMIT`
- **THEN** o registry MUST retornar o `ValueLimitEvaluator`

#### Scenario: Rejeita amount acima de maxPerPayment
- **GIVEN** uma politica VALUE_LIMIT com `maxPerPayment: 1000.00`
- **WHEN** `amount: 1500.00` for avaliado
- **THEN** o evaluator MUST retornar rejeicao com motivo `MAX_PER_PAYMENT_EXCEEDED`

#### Scenario: Aceita amount igual a maxPerPayment
- **GIVEN** uma politica VALUE_LIMIT com `maxPerPayment: 1000.00`
- **WHEN** `amount: 1000.00` for avaliado
- **THEN** o evaluator MUST retornar aprovacao

#### Scenario: Rejeita consumo acima do limite diurno
- **GIVEN** uma politica com `daytimeDailyLimit: 4000.00`
- **GIVEN** `consumedAmount: 3800.00` e `periodType: DAYTIME`
- **WHEN** `amount: 300.00` for avaliado
- **THEN** o evaluator MUST retornar rejeicao com motivo `DAILY_LIMIT_EXCEEDED`

#### Scenario: Aceita consumo exatamente igual ao limite diurno
- **GIVEN** uma politica com `daytimeDailyLimit: 4000.00`
- **GIVEN** `consumedAmount: 3800.00` e `periodType: DAYTIME`
- **WHEN** `amount: 200.00` for avaliado
- **THEN** o evaluator MUST retornar aprovacao

#### Scenario: Rejeita consumo acima do limite noturno
- **GIVEN** uma politica com `nighttimeDailyLimit: 1000.00`
- **GIVEN** `consumedAmount: 900.00` e `periodType: NIGHTTIME`
- **WHEN** `amount: 200.00` for avaliado
- **THEN** o evaluator MUST retornar rejeicao com motivo `DAILY_LIMIT_EXCEEDED`

#### Scenario: Aceita consumo exatamente igual ao limite noturno
- **GIVEN** uma politica com `nighttimeDailyLimit: 1000.00`
- **GIVEN** `consumedAmount: 900.00` e `periodType: NIGHTTIME`
- **WHEN** `amount: 100.00` for avaliado
- **THEN** o evaluator MUST retornar aprovacao

#### Scenario: Rejeita consumo acima do limite de final de semana
- **GIVEN** uma politica com `weekendDailyLimit: 1000.00`
- **GIVEN** `consumedAmount: 800.00` e `periodType: WEEKEND`
- **WHEN** `amount: 300.00` for avaliado
- **THEN** o evaluator MUST retornar rejeicao com motivo `DAILY_LIMIT_EXCEEDED`

#### Scenario: Aceita consumo exatamente igual ao limite de final de semana
- **GIVEN** uma politica com `weekendDailyLimit: 1000.00`
- **GIVEN** `consumedAmount: 800.00` e `periodType: WEEKEND`
- **WHEN** `amount: 200.00` for avaliado
- **THEN** o evaluator MUST retornar aprovacao

#### Scenario: Avaliador ignora campos de outras categorias
- **GIVEN** uma politica VALUE_LIMIT
- **WHEN** o evaluator for executado
- **THEN** campos como `dailyTransactionLimit` NAO DEVM ser verificados

#### Scenario: Avaliador retorna resultado com motivo e aprovacao
- **GIVEN** uma avaliacao de pagamento
- **WHEN** o `ValueLimitEvaluator` processar
- **THEN** o resultado MUST conter `approved: boolean` e `reason: string` quando rejeitado
