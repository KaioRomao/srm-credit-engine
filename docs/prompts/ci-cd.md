# Prompt: CI/CD, Git Hooks e Versionamento Semântico — SRM Credit Engine

Cole este prompt inteiro no Claude Code, dentro da raiz do repositório `srm-credit-engine`.

---

## Contexto do projeto

Este é o **SRM Credit Engine**, uma API Spring Boot 4.1.0 (Java 17) que precifica e liquida
recebíveis para um FIDC multimoedas. Stack: Maven, MySQL 8 (Flyway para migrations),
RabbitMQ (liquidação assíncrona com retry + DLQ), MapStruct, Lombok, springdoc-openapi.

Testes: `maven-failsafe-plugin` já roda os testes de integração (`**/*IT.java`) contra
MySQL e RabbitMQ reais (via `application-integracao.properties`, host/porta configuráveis
por variável de ambiente). JaCoCo já está configurado e gera relatório de cobertura
combinando unitários + integração.

O que **não existe ainda** e precisa ser criado:
- Nenhum linter/formatter configurado no `pom.xml`.
- Nenhum pipeline de CI/CD (`.github/workflows` não existe).
- Nenhum git hook (pre-commit/pre-push) configurado.
- Nenhuma tag de versionamento semântico no repositório.

O objetivo é fechar os requisitos de nível **Sênior** do desafio técnico, especificamente:
> CI/CD (pipeline rodando testes e linter) · Git Hooks (linters/testes antes do
> commit/push) · Semantic Versioning via tags.

---

## O que você deve entregar

### 1. Linter/formatter no Maven

Adicione o plugin **Spotless** (`com.diffplug.spotless:spotless-maven-plugin`) ao `pom.xml`,
configurado com `google-java-format` (ou `palantir-java-format`, tanto faz — escolha um e
justifique no README) para os diretórios `src/main/java` e `src/test/java`.

- `mvn spotless:check` deve falhar o build se algum arquivo estiver fora do padrão.
- `mvn spotless:apply` deve corrigir automaticamente.
- Rode `mvn spotless:apply` uma vez no projeto inteiro para não quebrar o build na primeira
  execução do CI, e comite o resultado num commit isolado (`style: apply spotless formatting`).

### 2. Pipeline de CI (GitHub Actions)

Crie `.github/workflows/ci.yml` com gatilho em `push` e `pull_request` para as branches
`development` e `main`. Estrutura sugerida (ajuste nomes de job/step como achar melhor):

- **Job `build-and-lint`**: checkout, setup JDK 17 (Temurin), cache de dependências Maven,
  `mvn -B spotless:check compile`.
- **Job `unit-tests`**: depende do anterior; roda `mvn -B test` (só os testes unitários,
  sem `*IT.java`); publica o relatório de testes como artifact (ex.: `surefire-reports`).
- **Job `integration-tests`**: depende do anterior; sobe **MySQL 8** e **RabbitMQ**
  como `services:` do GitHub Actions (com healthcheck configurado), define as env vars
  `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `RABBITMQ_HOST`,
  `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` apontando pros services, e roda
  `mvn -B verify` (o failsafe + jacoco já fazem o resto). Publique o relatório JaCoCo
  (`target/site/jacoco`) como artifact do job.
- **Job `docker-build`** (opcional, só em push pra `main`): valida que o `Dockerfile`
  builda com `docker build .` — não precisa publicar a imagem em lugar nenhum, só garantir
  que o build multi-stage não quebrou.

Documente no README, numa seção nova "## CI/CD", o que cada job faz e por que os testes de
integração precisam de services reais (a decisão de projeto foi contra MySQL/RabbitMQ de
verdade em vez de mocks/Testcontainers — mantenha essa filosofia no CI também, não troque
por H2 silenciosamente).

### 3. Git hooks (pre-commit / pre-push)

Este projeto é Maven puro, sem Node — então em vez de Husky (que é do ecossistema npm),
use a ferramenta **[pre-commit](https://pre-commit.com/)** (Python, agnóstica de linguagem,
é literalmente citada como exemplo no requisito do case).

- Crie `.pre-commit-config.yaml` na raiz com:
    - Um hook local de **pre-commit** que roda `mvn spotless:apply` (corrige formatação
      automaticamente antes do commit).
    - Um hook local de **pre-push** que roda `mvn test` (só os unitários — os de integração
      ficam pro CI, seriam lentos demais localmente).
- Documente no README, na seção de setup, os dois comandos de instalação:
  `pip install pre-commit` e `pre-commit install --hook-type pre-commit --hook-type pre-push`.
- Se preferir não depender de Python, alternativa aceitável: um script
  `scripts/install-git-hooks.sh` que copia hooks shell simples para `.git/hooks/pre-commit`
  e `.git/hooks/pre-push` fazendo a mesma coisa. Escolha uma das duas abordagens e não
  implemente as duas.

### 4. Versionamento semântico (tags)

- Adicione ao README uma seção "## Versionamento" explicando o processo de release:
  ao mergear `development` → `main`, cria-se uma tag anotada `vMAJOR.MINOR.PATCH`
  (Semantic Versioning) na `main`, com mensagem descrevendo o escopo da entrega.
- Crie a primeira tag do projeto: `git tag -a v1.0.0 -m "release: entrega do desafio SRM Credit Engine"`
  na `main`, depois do merge do PR `development → main`. Não crie a tag em `development`.
- Opcional, se quiser ir além: um step no job de CI (só quando o push for na `main`) que
  gera automaticamente as release notes a partir dos commits `feat:`/`fix:` desde a
  última tag (pode usar uma GitHub Action pronta tipo `googleapis/release-please-action`
  ou algo simples com `git log`).

---

## Regras gerais para esta tarefa

- Trabalhe na branch `development`, com commits pequenos e atômicos em **Conventional
  Commits**, exatamente como o resto do histórico do projeto já faz (`feat:`, `fix:`,
  `docs:`, `chore:`, `style:`, `ci:`).
- Não reescreva histórico existente nem force-push nada.
- Não invente dependências ou serviços externos além dos já citados (MySQL, RabbitMQ).
- Ao final, abra (ou descreva como abrir) o Pull Request `development → main` documentando
  no corpo do PR os quatro itens entregues.
