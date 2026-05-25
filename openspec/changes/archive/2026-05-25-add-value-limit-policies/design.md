## Context

O projeto ja possui carteiras funcionando (`POST /wallets`) e a politica `DEFAULT_VALUE_LIMIT` e inserida automaticamente na criacao de carteiras via `WalletDAOSpecImpl`. As tabelas `policies` e `wallet_policies` ja existem na migracao V1, mas nao ha endpoints para gerenciar politicas nem mecanismos de resolucao e avaliacao de politicas em tempo de execucao.

O dominio precisa de:
- Endpoints para CRUD de politicas `VALUE_LIMIT`.
- Mecanismo para resolver a politica ativa de uma carteira.
- Estrutura extensivel para suportar futuras categorias de politica (`TX_COUNT_LIMIT`).

## Goals / Non-Goals

**Goals:**
- Expor `POST /policies`, `GET /policies`, `GET /wallets/{walletId}/policies`, `PUT /wallets/{walletId}/policy`.
- Implementar `PolicyResolver`, `PolicyEvaluator` e `PolicyEvaluatorRegistry` como contratos de dominio.
- Adicionar constraint de unicidade: uma politica ativa por carteira.
- Garantir que `PUT /wallets/{walletId}/policy` desative a politica anterior e ative a nova.

**Non-Goals:**
- Processamento de pagamentos (sera na Fatia 4).
- `TX_COUNT_LIMIT` completo (sera na Fatia 8).
- Concorrencia de pagamentos (sera na Fatia 6).

## Decisions

### 1. PolicyResolver como interface de dominio
- **Decisao**: Criar `PolicyResolverSpec` no `boundary-context/input-boundary` que recebe `walletId` e retorna a politica ativa.
- **Alternativa**: Resolver diretamente no DAO. Rejeitada porque mistura responsabilidade de infra com regra de negocio.
- **Motivo**: O caso de uso de pagamento precisa resolver a politica sem conhecer detalhes de persistencia.

### 2. PolicyEvaluator e Registry para extensibilidade
- **Decisao**: Definir `PolicyEvaluatorSpec` como interface com metodo `evaluate(policy, context)` e `PolicyEvaluatorRegistrySpec` para registrar avaliadores por categoria.
- **Alternativa**: Switch/case sobre `category` no fluxo de pagamento. Rejeitada porque viola Open/Closed Principle.
- **Motivo**: Nova categoria de politica requer apenas registrar um novo evaluator, sem alterar o fluxo principal.

### 3. Separacao de rotas de politica
- **Decisao**: Criar `PolicyRoutes` em modulo separado de `WalletRoutes`, seguindo o padrao existente.
- **Motivo**: Coesao e manutencao. As rotas de politica tem escopo distinto das de carteira.

### 4. Constraint de unicidade via migracao V2
- **Decisao**: Adicionar `CREATE UNIQUE INDEX` filtrando `active = TRUE` para garantir uma politica ativa por carteira.
- **Alternativa**: Validar apenas em aplicacao. Rejeitada porque concorrencia futura pode burlar a validacao em memoria.
- **Motivo**: Garantia no banco de dados e preparacao para concorrencia (Fatia 6).

### 5. Reutilizacao da tabela `policies` existente
- **Decisao**: Usar a tabela `policies` ja criada na V1 com todos os campos de `VALUE_LIMIT`.
- **Motivo**: A migracao V1 ja preve `max_per_payment`, `daytime_daily_limit`, `nighttime_daily_limit`, `weekend_daily_limit` e `daily_transaction_limit`.

## Risks / Trade-offs

- [Migracao V2] → A constraint partial unique index `(wallet_id, active) WHERE active = TRUE` funciona no PostgreSQL mas nao em outros bancos. Aceitavel pois o banco e PostgreSQL.
- [PolicyEvaluatorRegistry como mapa em memoria] → Perde registros ao reiniciar. Aceitavel pois as categorias sao conhecidas em tempo de compilacao e registradas na inicializacao.
- [PUT /wallets/{walletId}/policy sem validacao de carteira existente] → Pode expor erro interno. Mitigado com validacao explicita no caso de uso.
