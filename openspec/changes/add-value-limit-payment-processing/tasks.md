## 1. ValueLimitEvaluator

- [x] 1.1 Criar interface `PolicyEvaluator` com metodo `evaluate(policy, amount, consumedAmount, periodType): EvaluationResult`
- [x] 1.2 Criar `ValueLimitEvaluator` implementando `PolicyEvaluator` com verificacao de `maxPerPayment`, `daytimeDailyLimit`, `nighttimeDailyLimit` e `weekendDailyLimit`
- [x] 1.3 Registrar `ValueLimitEvaluator` no `PolicyEvaluatorRegistry` para categoria `VALUE_LIMIT`
- [x] 1.4 Criar testes unitarios para `ValueLimitEvaluator` cobrindo todos os cenarios do spec

## 2. Tabelas de persistencia

- [x] 2.1 Criar migracao para tabela `payments` com colunas: `id`, `wallet_id`, `policy_id`, `amount`, `occurred_at`, `period_type`, `period_start`, `status`, `created_at`, `updated_at`
- [x] 2.2 Criar migracao para tabela `limit_consumptions` com colunas: `id`, `wallet_id`, `policy_id`, `period_type`, `period_start`, `consumed_amount`, `created_at`, `updated_at`
- [x] 2.3 Adicionar indices: `payments(wallet_id, occurred_at, id)` e `payments(wallet_id, policy_id, period_type, period_start)`
- [x] 2.4 Adicionar constraint unique em `limit_consumptions(wallet_id, policy_id, period_type, period_start)`
- [x] 2.5 Executar migracoes e verificar schema gerado

## 3. DAO de pagamentos

- [x] 3.1 Criar `PaymentGatewaySpec` com metodo `processPaymentInTransaction`
- [x] 3.2 Criar `PaymentGatewayImpl` com `processPaymentInTransaction` usando `SELECT ... FOR UPDATE` e transacao atomica
- [x] 3.3 Criar metodo `findOrLockConsumption` interno com `SELECT ... FOR UPDATE` para travar a linha de consumo
- [x] 3.4 Criar metodo `upsertConsumption` usando `INSERT ... ON CONFLICT DO UPDATE` para incrementar consumo
- [x] 3.5 Criar testes de integracao para pagamento com Testcontainers

## 4. Usecase ProcessPayment

- [x] 4.1 Criar `ProcessPaymentUseCaseSpec` interface
- [x] 4.2 Criar `ProcessPaymentUseCaseImpl` que valida carteira, resolve politica, classifica periodo, avalia limite e persiste atomicamente
- [x] 4.3 Validar existencia da carteira (retornar `404` se inexistente)
- [x] 4.4 Resolver politica ativa da carteira via `PolicyResolver`
- [x] 4.5 Classificar periodo a partir do `occurredAt` usando o dominio de periodos (Fatia 3)
- [x] 4.6 Obter `ValueLimitEvaluator` do registry para categoria da politica
- [x] 4.7 Envolver pagamento e consumo em transacao atomica com `FOR UPDATE` via `PaymentGateway`
- [x] 4.8 Se aprovado: inserir pagamento como `APPROVED` e upsert consumo na mesma transacao
- [x] 4.9 Se rejeitado por limite: retornar erro sem persistir nada (rollback implicito)
- [x] 4.10 `maxPerPayment` validado antes da transacao (retorna 400)

## 5. Endpoint HTTP

- [x] 5.1 Criar `PaymentRoutes` com rota `POST /wallets/{walletId}/payments`
- [x] 5.2 Implementar desserializacao de request body com `amount` e `occurredAt`
- [x] 5.3 Conectar rota ao `ProcessPaymentUseCase`
- [x] 5.4 Mapear erros: `404` para carteira inexistente, `400` para validacao, `422` para limite insuficiente
- [x] 5.5 Garantir response `201 Created` com `paymentId`, `status`, `amount`, `occurredAt`
- [x] 5.6 Registrar rotas no modulo Ktor
- [x] 5.7 Criar testes de integracao com `testApplication` para o endpoint

## 6. Validacoes de entrada

- [x] 6.1 Validar `amount` obrigatorio, maior que zero e menor ou igual a `maxPerPayment`
- [x] 6.2 Validar `occurredAt` obrigatorio e em formato ISO-8601 valido
- [x] 6.3 Validar `walletId` como UUID valido
- [x] 6.4 Retornar `400 Bad Request` com mensagem descritiva para cada validacao

## 7. Verificacao final

- [x] 7.1 Executar `./gradlew test` e confirmar que todos os testes passam
- [x] 7.2 Executar build completo da aplicacao
- [x] 7.3 Verificar cobertura dos cenarios do spec de payments
- [x] 7.4 Verificar cobertura dos cenarios do spec de value-limit-policy
