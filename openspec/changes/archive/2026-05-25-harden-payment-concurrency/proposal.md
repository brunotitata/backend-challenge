## Why

As implementacoes atuais de processamento de pagamento e idempotencia nao sao seguras sob concorrencia. Duas requisicoes simultaneas para a mesma carteira podem aprovar pagamentos que, somados, ultrapassam o limite disponivel, e a idempotencia pode ser violada se duas requisicoes simultaneas com a mesma chave forem processadas antes do registro da chave. Esta change introduz consistencia concorrente usando transacoes e bloqueio atomico de consumo.

## What Changes

- Adiciona tabela `limit_consumptions` para rastrear consumo agregado de forma atomica por carteira, politica e periodo
- Altera `ProcessPayment` para executar dentro de uma transacao com verificacao de limite e atualizacao de consumo como uma unidade atomica
- Implementa bloqueio pessimista ou UPSERT atomico para evitar condicoes de corrida no consumo
- Preserva a separacao de concorrencia por carteira: pagamentos de carteiras diferentes nao se bloqueiam
- Mantem idempotencia correta sob concorrencia usando a mesma transacao com constraint unique
- Cria cenario de teste concorrente que comprova que `700 + 700` com limite `1000` aprova apenas um pagamento

## Capabilities

### New Capabilities
- `payment-concurrency`: garantia de consistencia em pagamentos simultaneos para a mesma carteira, incluindo limite atomico e idempotencia concorrente

### Modified Capabilities
- `payments`: processamento de pagamento deve ser realizado dentro de transacao com bloqueio atomico de consumo; a verificacao de limite e a gravacao do consumo devem ser atomicas
- `payment-idempotency`: registro da chave idempotente e verificacao de conflito devem ocorrer dentro da mesma transacao do pagamento

## Impact

- Camada de persistencia: nova tabela `limit_consumptions` com constraint unique `(wallet_id, policy_id, period_type, period_start)`
- Usecase `ProcessPayment`: refatorado para executar em transacao unica com bloqueio de linha
- Migracao: adicionar criacao da tabela `limit_consumptions` e migracao de dados dos consumos existentes
- Testes: novos testes de concorrencia usando corrotinas ou threads simultaneas
- Nenhuma alteracao nos contratos HTTP
