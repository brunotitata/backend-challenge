## Context

O projeto ja possui base Kotlin + Ktor, camadas separadas por adaptadores, boundary-context, core e application, alem de migracoes Flyway para a fundacao. A criacao de carteiras e a primeira capacidade de negocio exposta pela API e precisa deixar o dominio pronto para pagamentos futuros, que dependem de uma politica ativa resolvivel por carteira.

A fatia deve preservar HTTP/JSON, respostas de erro padronizadas e datas serializadas em ISO-8601 UTC. A persistencia deve continuar usando PostgreSQL, com migracoes versionadas e constraints que protejam invariantes de negocio sempre que possivel.

## Goals / Non-Goals

**Goals:**

- Expor `POST /wallets` com contrato estavel para criacao de carteiras.
- Centralizar a regra de criacao em um caso de uso, mantendo a rota apenas como adaptador HTTP.
- Persistir carteiras com UUID, `ownerName`, `createdAt` e `updatedAt` quando aplicavel ao padrao de schema.
- Criar ou preparar a tabela de vinculo `wallet_policies` para associar a politica `DEFAULT_VALUE_LIMIT` como ativa na criacao da carteira.
- Manter o DDL limitado a estrutura fisica, como tabelas, chaves estrangeiras e indices.
- Manter a politica default resolvivel para as proximas fatias, mesmo que endpoints completos de politicas sejam implementados depois.

**Non-Goals:**

- Listar, atualizar ou remover carteiras.
- Criar endpoints publicos para gerenciamento completo de politicas.
- Processar pagamentos.
- Implementar idempotencia ou concorrencia de pagamentos.

## Decisions

### Criacao via caso de uso transacional

A criacao da carteira e a associacao da politica default devem ocorrer em uma unica unidade de trabalho. O caso de uso `CreateWallet` deve chamar uma porta de persistencia capaz de inserir a carteira e registrar o vinculo ativo com a politica default na mesma transacao.

Alternativa considerada: inserir a carteira no caso de uso e associar a politica em uma chamada separada. Rejeitada porque pode deixar carteiras sem politica ativa se a segunda operacao falhar.

### Politica default pela aplicacao

A politica `DEFAULT_VALUE_LIMIT` deve ser garantida pela camada de aplicacao durante a criacao da carteira, antes de registrar o vinculo ativo. O DDL nao deve inserir dados de negocio nem codificar limites default em migracao.

Alternativa considerada: criar a politica default por migracao/seed. Rejeitada para manter as migracoes Flyway restritas a estrutura fisica do banco.

### DDL sem regras de negocio

As migracoes devem criar tabelas, chaves estrangeiras e indices necessarios para consulta e integridade referencial, sem `CHECK` de limites, inserts de politicas default ou indices unicos parciais que codifiquem regras de negocio como uma politica ativa por carteira.

Alternativa considerada: impor regras por constraints no banco. Rejeitada para manter regras de negocio na aplicacao e deixar o DDL apenas estrutural.

### Validacao em borda e dominio

A rota deve rejeitar body invalido e `ownerName` ausente, nulo, vazio ou em branco com `400 Bad Request`. O caso de uso tambem deve validar `ownerName` em branco para proteger chamadas internas e testes unitarios.

Alternativa considerada: validar apenas no DTO/rota. Rejeitada porque deixaria o caso de uso dependente da disciplina dos adaptadores.

## Risks / Trade-offs

- Politicas completas ainda nao existem nesta fatia -> A migracao deve modelar apenas tabelas e indices necessarios sem inserir dados de negocio.
- Carteira criada sem politica por falha parcial -> Usar transacao para garantir politica default, inserir carteira e registrar vinculo ativo juntos.
- Invariante nao protegido por constraint de negocio no banco -> Manter a regra nos fluxos de aplicacao e reforcar nas fatias futuras de gerenciamento de politicas.
- Contrato de data inconsistente -> Serializar `createdAt` em UTC e cobrir o formato em teste de API.
