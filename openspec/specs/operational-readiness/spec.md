# operational-readiness Specification

## Purpose
TBD - created by archiving change add-operational-readiness. Update Purpose after archive.
## Requirements
### Requirement: Documentação técnica completa no README
O sistema MUST possuir um README.md na raiz do repositório contendo instruções claras para execução local, execução de testes e subida de dependências.

#### Scenario: Desenvolvedor clona o repositório
- **WHEN** um desenvolvedor clona o repositório
- **THEN** ele MUST conseguir executar a aplicação seguindo apenas as instruções do README

#### Scenario: README explica decisões arquiteturais
- **WHEN** o README é consultado
- **THEN** ele MUST conter seções explicando decisões de modelagem de domínio, idempotência, concorrência, timezone e precisão monetária

#### Scenario: README justifica escolhas técnicas
- **WHEN** o README é consultado
- **THEN** ele MUST justificar a escolha do PostgreSQL em relação ao diferencial de MongoDB do desafio
- **AND** ele MUST justificar o uso do jOOQ como camada de queries tipadas e alternativa ao JDBC manual

#### Scenario: README lista endpoints e exemplos
- **WHEN** o README é consultado
- **THEN** ele MUST listar todos os endpoints disponíveis
- **AND** ele MUST conter exemplos de request e response para os principais fluxos

### Requirement: Docker Compose para dependências locais
O sistema MUST fornecer um arquivo docker-compose.yml capaz de subir o banco de dados PostgreSQL localmente.

#### Scenario: Subir dependências via Docker Compose
- **WHEN** o comando docker compose up é executado
- **THEN** o PostgreSQL MUST estar acessível na porta configurada
- **AND** as migrações MUSTm ser capazes de executar contra esse banco

#### Scenario: Docker Compose opcional para aplicação
- **WHEN** o docker-compose.yml inclui o serviço da aplicação
- **THEN** a aplicação MUST iniciar e conectar-se ao banco automaticamente

### Requirement: Workflow de CI com build e testes
O sistema MUST possuir um workflow de CI configurado para executar build e testes automatizados em push e pull requests.

#### Scenario: CI executa com sucesso
- **WHEN** um push ou pull request é realizado
- **THEN** o workflow MUST executar ./gradlew test com sucesso
- **AND** o workflow MUST garantir que a aplicação compila

#### Scenario: CI provisiona banco de dados
- **WHEN** o workflow de CI executa testes que exigem banco de dados
- **THEN** o workflow MUST provisionar um PostgreSQL (service container ou similar)
- **AND** os testes MUSTm passar nesse ambiente limpo

### Requirement: Revisão final dos contratos de API
O sistema MUST ter seus endpoints obrigatórios revisados para garantir conformidade com o README do desafio.

#### Scenario: Endpoints obrigatórios retornam contratos corretos
- **WHEN** cada endpoint obrigatório é testado manualmente ou automaticamente
- **THEN** as respostas MUSTm corresponder exatamente aos contratos esperados pelo README

### Requirement: Logs estruturados com request ID
O sistema MUST gerar logs estruturados que incluam um identificador único de requisição.

#### Scenario: Cada requisição possui request ID
- **WHEN** uma requisição HTTP é recebida
- **THEN** um request ID MUST ser gerado ou extraído do header
- **AND** todos os logs gerados durante o processamento dessa requisição MUSTm conter esse request ID

### Requirement: Métricas simples de pagamentos
O sistema MUST expor ou registrar métricas simples sobre pagamentos aprovados e recusados.

#### Scenario: Métricas de pagamentos são coletadas
- **WHEN** um pagamento é processado
- **THEN** a métrica correspondente (aprovado ou recusado) MUST ser incrementada

### Requirement: Auditoria de eventos de pagamento
O sistema MUST registrar eventos de auditoria para decisões de aprovação ou rejeição de pagamentos.

#### Scenario: Evento de auditoria é registrado
- **WHEN** um pagamento é aprovado ou rejeitado
- **THEN** um evento de auditoria MUST ser persistido ou logado contendo walletId, amount, status, timestamp e motivo (se aplicável)

### Requirement: OpenAPI ou Postman Collection
O sistema MUST fornecer documentação de API em formato OpenAPI (Swagger) ou Postman Collection e Environment.

#### Scenario: Documentação de API acessível
- **WHEN** o endpoint /docs (Swagger UI) é acessado ou a Collection é importada no Postman
- **THEN** todos os endpoints obrigatórios MUSTm estar documentados com exemplos de request e response

### Requirement: Projeto executável do zero
O projeto MUST ser clonável e executável seguindo apenas as instruções do README.

#### Scenario: Execução do zero
- **WHEN** o projeto é clonado em um ambiente limpo
- **THEN** seguindo o README, um desenvolvedor MUST conseguir subir dependências, executar migrações, compilar e rodar testes sem erros

### Requirement: Checklist de submissão do desafio
O README MUST conter um checklist ou instruções sobre como submeter o desafio.

#### Scenario: Checklist de submissão
- **WHEN** o README é consultado na seção de submissão
- **THEN** ele MUST informar que o repositório deve ser privado no GitHub
- **AND** ele MUST informar que a permissão de leitura deve ser concedida para @tracefinancedev
- **AND** ele MUST informar que o e-mail para backend@trace.finance com assunto "Vaga Back-end" deve ser enviado com a URL do repositório

