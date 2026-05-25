## ADDED Requirements

### Requirement: Consistencia em pagamentos concorrentes
O sistema DEVE garantir que acessos simultaneos ao endpoint `POST /wallets/{walletId}/payments` para a mesma carteira nao ultrapassem os limites definidos pela politica ativa. A verificacao de limite e a gravacao do consumo DEVEM ser atomicas.

#### Scenario: Pagamentos simultaneos nao ultrapassam limite diurno
- **GIVEN** uma carteira com politca DEFAULT_VALUE_LIMIT ativa (`daytimeDailyLimit: 4000.00`)
- **GIVEN** a carteira ja consumiu `3500.00` no periodo diurno atual
- **GIVEN** a linha em `limit_consumptions` existe para a chave de consumo
- **WHEN** duas requisicoes simultaneas de `600.00` forem enviadas para o mesmo periodo
- **THEN** o sistema DEVE aprovar apenas um pagamento
- **AND** o outro DEVE retornar `422 Unprocessable Entity`

#### Scenario: Pagamentos simultaneos sem linha previa de consumo
- **GIVEN** uma carteira com politca DEFAULT_VALUE_LIMIT ativa (`daytimeDailyLimit: 4000.00`)
- **GIVEN** a carteira nao possui nenhum consumo registrado
- **GIVEN** nao existe linha em `limit_consumptions` para a chave de consumo
- **WHEN** duas requisicoes simultaneas de `2500.00` forem enviadas para o mesmo periodo
- **THEN** o sistema DEVE aprovar apenas o primeiro pagamento
- **AND** o segundo DEVE retornar `422 Unprocessable Entity`

#### Scenario: Cenario 700 + 700 com limite 1000 aprova apenas um
- **GIVEN** uma carteira com politca DEFAULT_VALUE_LIMIT ativa (`daytimeDailyLimit: 1000.00`)
- **GIVEN** a carteira nao possui consumo no periodo atual
- **WHEN** duas requisicoes simultaneas de `700.00` forem enviadas
- **THEN** o sistema DEVE aprovar apenas um pagamento
- **AND** o outro DEVE retornar `422 Unprocessable Entity`

#### Scenario: Pagamentos de carteiras diferentes nao se bloqueiam
- **GIVEN** duas carteiras distintas `wallet-A` e `wallet-B`, ambas com limite `1000.00` disponivel
- **WHEN** requisicoes simultaneas de `1000.00` forem enviadas para cada carteira
- **THEN** ambos os pagamentos DEVEM ser aprovados com `201 Created`

### Requirement: Insercao atomica da linha de consumo
O sistema DEVE garantir que a linha em `limit_consumptions` seja inserida atomicamente antes do lock, prevenindo que duas requisicoes concorrentes vejam `consumed_amount = 0` simultaneamente.

#### Scenario: Insert ON CONFLICT previne condicao de corrida
- **GIVEN** nao existe linha em `limit_consumptions` para a chave `(wallet_id, policy_id, period_type, period_start)`
- **WHEN** duas requisicoes simultaneas tentarem criar a linha
- **THEN** apenas uma DEVE criar a linha com `consumed_amount = 0`
- **AND** a segunda DEVE reutilizar a linha existente com o lock adquirido
