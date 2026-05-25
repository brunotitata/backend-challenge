## 1. Migracao e Banco de Dados

- [x] 1.1 Criar migracao Flyway para tabela `payment_idempotency_keys` com colunas `id`, `wallet_id`, `idempotency_key`, `request_hash`, `payment_id`, `response_status`, `response_body`, `created_at`, `updated_at`
- [x] 1.2 Adicionar constraint unique `(wallet_id, idempotency_key)` na migracao
- [x] 1.3 Registrar nova migracao no schema jOOQ e regenerar classes

## 2. Infraestrutura de Idempotencia

- [x] 2.1 Criar classe `IdempotencyRecord` no pacote de dominio
- [x] 2.2 Criar interface `IdempotencyRepository` com metodos `findByWalletAndKey`, `save`
- [x] 2.3 Implementar `IdempotencyRepository` com jOOQ usando a tabela `payment_idempotency_keys`
- [x] 2.4 Implementar metodo de calculo de request hash (SHA-256 de `amount` + `occurredAt` serializados)

## 3. Validacao do Header

- [x] 3.1 Criar funcao para extrair e validar o header `Idempotency-Key` da requisicao
- [x] 3.2 Validar header ausente retorna `400 Bad Request`
- [x] 3.3 Validar header vazio ou em branco retorna `400 Bad Request`
- [x] 3.4 Validar header acima de 255 caracteres retorna `400 Bad Request`

## 4. Integracao no Fluxo de Pagamento

- [x] 4.1 Modificar `ProcessPayment` usecase para receber e processar `Idempotency-Key`
- [x] 4.2 Implementar logica de verificar chave existente antes de processar pagamento
- [x] 4.3 Implementar logica de comparacao de request hash para detectar payload conflitante
- [x] 4.4 Implementar retorno de resposta cacheada quando chave e payload identicos
- [x] 4.5 Implementar retorno `409 Conflict` quando chave existe com payload diferente
- [x] 4.6 Registrar chave idempotente apos aceitacao da requisicao para processamento
- [x] 4.7 Garantir que validacoes `400 Bad Request` nao registrem chave idempotente

## 5. Atualizacao de Router e Serializacao

- [x] 5.1 Atualizar rota `POST /wallets/{walletId}/payments` para extrair `Idempotency-Key` do header
- [x] 5.2 Adicionar tratamento de erro para `409 Conflict` com corpo padronizado

## 6. Testes

- [x] 6.1 Teste unitario: calculo de request hash produz resultado deterministico
- [x] 6.2 Teste de integracao: retry identico nao duplica pagamento e retorna mesmo `paymentId`
- [x] 6.3 Teste de integracao: retry com `amount` diferente retorna `409`
- [x] 6.4 Teste de integracao: retry com `occurredAt` diferente retorna `409`
- [x] 6.5 Teste de integracao: mesma chave em carteiras diferentes nao conflita
- [x] 6.6 Teste de integracao: header ausente retorna `400`
- [x] 6.7 Teste de integracao: header vazio retorna `400`
- [x] 6.8 Teste de integracao: header acima do tamanho maximo retorna `400`
- [x] 6.9 Teste de integracao: idempotencia nao consome limite duas vezes
- [x] 6.10 Teste de integracao: validacao `400` nao registra chave idempotente
- [x] 6.11 Teste de integracao: constraint unique do banco rejeita duplicata
- [x] 6.12 Teste de integracao: chave registrada apos pagamento aprovado permite retry
- [x] 6.13 Teste de integracao: chave registrada apos pagamento rejeitado por `422` retorna `422` no retry
