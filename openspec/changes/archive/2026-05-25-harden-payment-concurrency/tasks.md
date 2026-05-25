## 1. Infraestrutura de transacao

- [x] 1.1 Estender `PaymentGatewaySpec.processPaymentInTransaction` para receber `idempotencyKey` e `requestHash`, retornando um `TransactionResult` selado (Approved, Rejected, Conflict, IdempotentReplay)
- [x] 1.2 Atualizar `PaymentGatewayImpl` para inserir linha `limit_consumptions` com `ON CONFLICT DO NOTHING` antes do `SELECT FOR UPDATE`, garantindo que a linha sempre exista para lock
- [x] 1.3 Adicionar logica de idempotencia dentro da transacao: tentar `INSERT ... ON CONFLICT DO NOTHING` em `payment_idempotency_keys`; se nao inseriu, consultar registro existente e retornar conflito ou replay
- [x] 1.4 Preservar o fluxo atual de verificacao de limite, insert do pagamento e UPSERT de consumo dentro da mesma transacao

## 2. Refatoracao do use case

- [x] 2.1 Remover checagem de idempotencia (`findByWalletAndKey`) e salvamento (`save`) de `ProcessPaymentUseCaseImpl` que estao fora da transacao
- [x] 2.2 Adaptar `ProcessPaymentUseCaseImpl.execute` para delegar a logica de idempotencia para o gateway na transacao
- [x] 2.3 Lidar com os resultados de `TransactionResult` no use case: Approved → retornar payment, Rejected → lancar 422, Conflict → lancar 409, IdempotentReplay → retornar payment existente
- [x] 2.4 Garantir que a resposta idempotente (replay) preserve o status code e body originais, sem consumir limite novamente

## 3. Testes de concorrencia

- [x] 3.1 Adicionar teste de pagamentos simultaneos para mesma carteira no mesmo periodo: duas requisicoes de `700.00` com limite `1000.00`, apenas uma aprovada
- [x] 3.2 Adicionar teste de pagamentos simultaneos sem linha previa de `limit_consumptions`: duas requisicoes concorrentes, apenas uma aprovada
- [x] 3.3 Adicionar teste de idempotencia concorrente com mesma chave e mesmo payload: ambas as requisicoes retornam sucesso com mesmo `paymentId`
- [x] 3.4 Adicionar teste de idempotencia concorrente com mesma chave e payload diferente: uma processada, outra retorna `409`
- [x] 3.5 Adicionar teste de pagamentos simultaneos para carteiras diferentes: ambas aprovadas sem bloqueio mutuo
- [x] 3.6 Verificar que os testes de limite existentes continuam funcionando apos a refatoracao
