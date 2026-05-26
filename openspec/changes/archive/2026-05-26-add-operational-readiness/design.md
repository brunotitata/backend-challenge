## Context

Todas as fatias de implementação foram concluídas. A aplicação possui:
- Criação de carteiras com política default.
- Políticas `VALUE_LIMIT` e `TX_COUNT_LIMIT`.
- Processamento de pagamentos com classificação de período.
- Idempotência via header `Idempotency-Key`.
- Consistência em concorrência com transações e bloqueios.
- Listagem paginada de pagamentos com filtro por data.
- Testes unitários e de integração.

O projeto foi construído com Kotlin + Ktor, PostgreSQL e jOOQ.

## Goals / Non-Goals

**Goals:**
- Documentar decisões técnicas e arquiteturais no README.
- Instruir execução local, testes e dependências de forma clara.
- Configurar Docker Compose para banco de dados local (e opcionalmente aplicação).
- Configurar CI com build e testes automatizados.
- Adicionar observabilidade: logs estruturados, métricas simples e auditoria.
- Disponibilizar OpenAPI/Swagger ou Postman Collection.
- Revisar contratos de API finais conforme README do desafio.
- Garantir que o projeto possa ser clonado e executado seguindo apenas o README.

**Non-Goals:**
- Alterar regras de negócio existentes.
- Modificar contratos de API (apenas revisão e validação).
- Otimizações avançadas de performance.
- Deploy em ambiente produtivo.

## Decisions

### README Técnico
- **Decisão**: Centralizar toda a documentação em um `README.md` principal na raiz.
- **Justificativa**: O desafio solicita README técnico; um único arquivo facilita a submissão e revisão.
- **Conteúdo**: visão geral, como rodar, testes, endpoints, exemplos, decisões de modelagem, idempotência, concorrência, timezone, precisão monetária, justificativa PostgreSQL + jOOQ, trade-offs.

### Docker Compose
- **Decisão**: Manter `docker-compose.yml` com PostgreSQL e, se possível, a aplicação.
- **Justificativa**: Facilita execução local sem configuração manual de banco. O desafio menciona MongoDB com Docker como diferencial; PostgreSQL com Docker Compose é equivalente e mais alinhado à stack escolhida.

### CI/CD
- **Decisão**: GitHub Actions com workflow de build e testes em push/PR.
- **Justificativa**: Padroniza qualidade e garante que testes passem antes de merge. Gratuito para repositórios privados no GitHub.

### Observabilidade
- **Decisão**: Logs estruturados com identificador de requisição (request ID).
- **Justificativa**: Facilita rastreamento de requisições em debug e demonstra atenção a observabilidade.
- **Decisão**: Métricas simples expostas em endpoint (`/metrics` ou log) contando pagamentos aprovados/recusados.
- **Justificativa**: Diferencial do desafio; não exige infraestrutura complexa.
- **Decisão**: Auditoria de eventos de pagamento em tabela separada ou log estruturado.
- **Justificativa**: Permite rastrear histórico de decisões de aprovação/rejeição.

### OpenAPI
- **Decisão**: Usar Swagger UI via Ktor plugin ou gerar `openapi.yaml` estático.
- **Justificativa**: Diferencial do desafio; facilita testes manuais e integração.

### Revisão de Contratos
- **Decisão**: Validar cada endpoint obrigatório contra o README do desafio antes de finalizar.
- **Justificativa**: Garantir compatibilidade total com o que o avaliador espera.

## Risks / Trade-offs

- [Risco] Tempo insuficiente para implementar todos os diferenciais (logs, métricas, auditoria, OpenAPI).
  - [Mitigação] Priorizar README, Docker Compose e CI como obrigatórios. Diferenciais são opcionais e podem ser documentados como não implementados por restrição de tempo.
- [Risco] Divergência entre README do desafio e implementação atual.
  - [Mitigação] Checklist manual de validação de cada endpoint e regra de negócio.
- [Risco] CI falha em ambiente limpo (GitHub Actions) por falta de banco de dados.
  - [Mitigação] Configurar service container de PostgreSQL no workflow ou usar banco em memória (H2) para testes, se compatível com jOOQ.

## Migration Plan

Não aplicável — esta change não altera código de produção, apenas documentação, infraestrutura e observabilidade.

## Open Questions

- **OpenAPI será gerado automaticamente ou manualmente?** → Será gerado automaticamente, via plugin do Ktor ou gerador estático a partir das rotas definidas.
- **Métricas serão expostas em endpoint HTTP ou apenas em logs?** → Apenas em logs estruturados, sem endpoint dedicado. Contadores de pagamentos aprovados e recusados serão emitidos como linhas de log.
- **Auditoria será tabela separada ou log estruturado?** → Tabela separada no banco de dados (`payment_audit_events`), persistindo walletId, amount, status, timestamp e motivo de rejeição.
