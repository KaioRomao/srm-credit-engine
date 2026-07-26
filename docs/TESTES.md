# Estratégia de Testes — SRM Credit Engine

**208 testes · 0 falhas · 97,9% de instruções · 92,5% de branches**

---

## 1. Objetivo

Em sistema financeiro, teste não serve para atingir métrica de cobertura — serve para impedir que uma refatoração altere silenciosamente um valor monetário. A suíte foi construída em torno de três perguntas:

1. **O cálculo está certo?** Valor presente, spread por tipo de recebível, conversão cambial e arredondamento têm valores esperados fixados, não asserções genéricas.
2. **O dinheiro pode duplicar ou desaparecer?** Idempotência, máquina de estados, guard 1:1 e atomicidade do intake são testados nos caminhos que os violariam.
3. **A falha é tratada da forma correta?** Erro determinístico não deve consumir retry; falha inesperada não deve vazar detalhe técnico; nenhuma mensagem pode ser descartada em silêncio.

---

## 2. Pirâmide de testes

```
                    ┌───────────────────────┐
                    │  Resiliencia (4)      │  retry em conflito de versao
                    │  RetryOptimisticLockIT│  MockitoSpyBean + contagem
                    ├───────────────────────┤
                    │  Concorrência (5)     │  threads reais + @Version
                    │  ConcorrenciaIT       │  optimistic lock e constraints
                    ├───────────────────────┤
                    │  Integração (5)       │  @SpringBootTest + MySQL + RabbitMQ
                    │  FluxoCompletoIT      │  fluxo ponta a ponta, real
                    ├───────────────────────┤
                    │  Repositório (15)     │  @DataJpaTest + MySQL real
                    │  ExtratoRepositoryIT  │  SQL nativo, paginação, whitelist
                    ├───────────────────────┤
                    │  Web (48)             │  @WebMvcTest + MockMvc
                    │  5 controllers        │  contrato HTTP e status semântico
                    ├───────────────────────┤
                    │  Unitário (126)       │  JUnit 5 + Mockito, sem contexto
                    │  services · strategy  │  regra de negócio isolada
                    │  enum · messaging     │  milissegundos por teste
                    │  client               │
                    └───────────────────────┘
```

A base é larga de propósito: **179 dos 208 testes rodam sem subir contexto Spring**, o que mantém o ciclo de feedback em segundos. Só sobe infraestrutura onde ela é o objeto do teste — SQL nativo e fluxo assíncrono.

---

## 3. Organização

```
src/test/java/br/com/srm/credit/engine/
├── client/
│   └── CambioClientTest                    8 testes · MockRestServiceServer
├── controller/
│   ├── CambioControllerTest                7 testes · @WebMvcTest
│   ├── ExtratoControllerTest               8 testes
│   ├── LiquidacaoControllerTest           16 testes
│   ├── LoteControllerTest                  9 testes
│   └── PrecificacaoControllerTest          8 testes
├── enums/
│   └── StatusLiquidacaoTest               41 testes · parametrizados
├── messaging/
│   ├── LiquidacaoConsumerTest              9 testes
│   ├── LiquidacaoMessageRecovererTest      3 testes
│   └── LiquidacaoProducerTest              2 testes
├── repository/
│   └── ExtratoRepositoryIT                15 testes · @DataJpaTest
├── service/impl/
│   ├── CambioServiceImplTest              12 testes
│   ├── LiquidacaoServiceImplTest          30 testes
│   ├── LoteServiceImplTest                 6 testes
│   └── PrecificacaoServiceImplTest        18 testes
├── service/strategy/
│   ├── ChequePreDatadoStrategyTest         4 testes
│   └── DuplicataMercantilStrategyTest      6 testes
├── support/
│   └── DadosDeTeste                        fábrica de entidades
├── ConcorrenciaLiquidacaoIT                5 testes · threads reais
├── RetryOptimisticLockIT                   4 testes · retry declarativo
└── FluxoCompletoIT                         5 testes · fluxo ponta a ponta
```

O pacote de teste **espelha o pacote de produção**. Localizar o teste de uma classe não exige busca.

---

## 4. Nomenclatura

Padrão adotado: **`deveFazerXQuandoY()`**, com `@DisplayName` em português para leitura do relatório.

```java
@Test
@DisplayName("deve registrar FALHA e confirmar quando a causa é ErroDeNegocio")
void deveRegistrarFalhaEConfirmarQuandoCausaEhErroDeNegocio() { ... }
```

Duas regras que valem mais que o padrão em si:

- **O `Quando` descreve a condição, não a implementação.** `deveIgnorarMensagemDuplicadaQuandoStatusNaoEhPendente` diz o cenário; se o guard mudar de `if` para `switch`, o nome continua correto.
- **`@DisplayName` explica o comportamento de negócio.** O relatório de teste vira documentação legível por quem não conhece o código.

Classes de teste usam `@Nested` para agrupar por método público:

```
LiquidacaoServiceImpl
├── iniciaLiquidacao      6 testes
├── processaLiquidacao    6 testes
├── finalizaLiquidacao    7 testes
├── registrarFalha        8 testes
└── consultaLiquidacao    3 testes
```

---

## 5. Padrão AAA

Todos os testes seguem **Arrange · Act · Assert**, separados por linha em branco — sem comentários marcando as seções:

```java
@Test
@DisplayName("deve aplicar taxa inversa quando só existe cotação no sentido oposto")
void deveAplicarTaxaInversaQuandoSoExisteCotacaoNoSentidoOposto() {
    when(cambioRepository.buscarUltimoCambio("USD", "BRL")).thenReturn(Optional.empty());
    when(cambioRepository.buscarUltimoCambio("BRL", "USD"))
            .thenReturn(Optional.of(cambio(new BigDecimal("0.200000"))));

    BigDecimal convertido = cambioService.converter(new BigDecimal("100.0000"), "USD", "BRL");

    assertThat(convertido)
            .as("1 / 0.2 = 5, logo 100 USD = 500 BRL")
            .isEqualByComparingTo("500.0000");
}
```

O `.as(...)` do AssertJ carrega o **porquê** do número esperado. Quando o teste falha, a mensagem explica a regra em vez de só mostrar a diferença.

---

## 6. O que cada camada verifica

### 6.1 Strategy — o núcleo financeiro

Valores **fixados**, não relativos:

| Cenário | Esperado |
|---|---|
| Duplicata, R$ 10.000, taxa 1% a.m., 87 dias | `9308.9520` |
| Cheque, mesmos parâmetros | `9050.5086` |
| Spread duplicata | `0.015` |
| Spread cheque | `0.025` |

Além disso: prazo maior gera deságio maior, taxa maior gera deságio maior, e cheque sempre rende menos que duplicata nos mesmos parâmetros.

> **Um teste meu estava errado, não o código.** Assertei proporcionalidade exata ao dobrar o valor de face. Falhou por `0.0001`: o arredondamento em 4 casas acontece **por chamada**, logo `2 × round(x) ≠ round(2x)`. O teste passou a usar `isCloseTo` com offset de uma unidade da última casa — e agora documenta esse comportamento.

### 6.2 Máquina de estados — 41 testes parametrizados

A matriz **completa** de transições, positivas e negativas:

```java
@ParameterizedTest(name = "{0} -> {1} deve ser rejeitada")
@CsvSource({
        "PENDENTE, LIQUIDADA", "PENDENTE, PENDENTE",
        "PROCESSANDO, PENDENTE", "PROCESSANDO, CANCELADA",
        "LIQUIDADA, PROCESSANDO", "FALHA, LIQUIDADA", ...
})
void deveRejeitarTransicaoQuandoNaoEstaNaTabela(StatusLiquidacao origem, StatusLiquidacao destino) { ... }
```

Mais: todo estado terminal rejeita **todos** os destinos (loop sobre `values()`), destino nulo é sempre rejeitado, e a lista de constantes bate com o `CHECK` constraint do banco — se alguém adicionar um estado no enum sem migrar o schema, o teste falha.

### 6.3 Idempotência e concorrência

| Cenário | Teste |
|---|---|
| Mesmo `trackId` reenviado | devolve a liquidação existente, `save` nunca é chamado, evento não é publicado |
| Precificação já liquidada | `ConflitoNegocioException` → 409, nada é persistido |
| Mensagem duplicada em `PROCESSANDO` | ignorada — parametrizado sobre os 4 estados não-`PENDENTE` |
| Mensagem duplicada em estado terminal | `finalizaLiquidacao` não toca o `CambioService` |

> O teste `deveIgnorarMensagemDuplicadaQuandoStatusNaoEhPendente` cobre o bug real que o guard antigo (`if status == LIQUIDADA`) permitia: reentrada num `PROCESSANDO` disparava **conversão de câmbio em dobro**.

### 6.4 Classificação de falha na fila

```java
@ParameterizedTest
@MethodSource("errosDeNegocio")
void deveRegistrarFalhaEConfirmarQuandoCausaEhErroDeNegocio(Throwable causa) {
    doThrow(causa).when(liquidacaoService).finalizaLiquidacao(LIQUIDACAO_ID);

    assertThatCode(() -> consumer.consumir(MENSAGEM)).doesNotThrowAnyException();

    verify(liquidacaoService).registrarFalha(LIQUIDACAO_ID, causa);
}
```

As 5 exceções marcadas como `ErroDeNegocio` são testadas via `@MethodSource`. O caso complementar — exceção **não** marcada — verifica que ela é **relançada** (`isSameAs`), porque é isso que faz o retry agir.

O `LiquidacaoMessageRecoverer` tem o teste que mais importa: **republica na DLQ mesmo quando o registro de FALHA falha**, e mesmo quando o payload é ilegível. A mensagem nunca é descartada em silêncio.

### 6.5 Segurança da mensagem de falha

| Causa | `dsObservacao` esperado |
|---|---|
| `CambioException("Cotação não encontrada para BRL->USD")` | a própria mensagem |
| `NullPointerException("valor is null")` | texto genérico — asserção usa `doesNotContain("valor is null")` |
| Mensagem com 900 caracteres | truncada em exatamente 500 |
| Exceção sem mensagem | `null`, sem quebrar |

### 6.6 Contrato HTTP — 48 testes

Todos os status do contrato, por endpoint:

| Status | Onde é verificado |
|---|---|
| `200` | simular, consultar cotação, consultar liquidação, extrato |
| `201` | criar lote, sincronizar câmbio |
| `202` | solicitar liquidação |
| `400` | 14 variantes: campo ausente, valor negativo, valor zero, campo em branco, JSON malformado, header ausente, UUID inválido, path variable não numérico, data fora do ISO, período invertido, ordenação fora da whitelist |
| `404` | precificação inexistente, liquidação inexistente |
| `405` | `GET` em rota de `POST` |
| `409` | precificação já liquidada |
| `415` | `Content-Type: text/plain` |
| `422` | tipo sem strategy, moeda inexistente, prazo inválido, sem cotação |
| `500` | verifica que a mensagem é **fixa** e o detalhe interno não vaza |

> `204` e os verbos `PUT`/`DELETE` não são testados porque **não existem na API**. Nenhum recurso é atualizado ou removido: a liquidação é imutável por design e o cancelamento (que seria o candidato natural a `DELETE`) não tem endpoint. Testar status inexistente seria teatro.

### 6.7 Repositório — SQL nativo contra MySQL real

15 testes cobrindo o que só um banco real valida:

- Somente `LIQUIDADA` entra no extrato — a base de teste tem `FALHA`, `PENDENTE` e `CANCELADA` de propósito
- **Período inclusivo na ponta final**: uma liquidação às `23:30` do último dia do filtro precisa aparecer. É o teste que provaria a regressão se alguém trocar o `< dataFim+1` por `<= dataFim`
- Filtros isolados e combinados; normalização de `" usd "` para `USD`
- Paginação: `first`/`last`/`totalPages`, teto de 100 quando o cliente pede 5000
- Ordenação simples, múltipla, e **rejeição de injeção de SQL** pelo `sort`
- Projeção completa do snapshot (`vlSpread`, `vlTaxaBase`, `qtPrazoDia`)

### 6.8 Integração — fluxo real ponta a ponta

`FluxoCompletoIT` sobe o contexto completo contra MySQL e RabbitMQ reais:

```
POST /lotes (201) → POST /liquidacoes (202 PENDENTE)
                  → [fila] → consumer → LIQUIDADA
                  → GET /liquidacoes/{id} (200) → GET /extrato (200)
```

O polling do estado assíncrono usa **Awaitility**, não `Thread.sleep`:

```java
await().atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(300))
        .untilAsserted(() -> {
            Liquidacao liquidacao = liquidacaoRepository.findById(liquidacaoId).orElseThrow();
            assertThat(liquidacao.getStatus()).isEqualTo(StatusLiquidacao.LIQUIDADA);
        });
```

Também cobre: replay do mesmo `trackId` gerando **uma** liquidação, `409` na segunda liquidação da mesma precificação, rollback do lote inteiro quando um item é inválido, e disponibilidade do OpenAPI.

### 6.9 Concorrência — `@Version` e constraints

`ConcorrenciaLiquidacaoIT` usa **threads reais** coordenadas por `CountDownLatch`, não simulação com mock.

**Optimistic lock determinístico.** Duas transações `REQUIRES_NEW` em threads distintas: a lenta carrega a entidade e espera; a rápida carrega, atualiza e commita; a lenta então tenta gravar com a versão obsoleta.

```java
Future<Throwable> transacaoLenta = executor.submit(() -> {
    transacaoIsolada.execute(status -> {
        Liquidacao liquidacao = liquidacaoRepository.findById(liquidacaoId).orElseThrow();
        primeiraCarregou.countDown();
        aguardar(segundaCommitou);
        liquidacao.setDsObservacao("atualizacao da transacao lenta");
        liquidacaoRepository.saveAndFlush(liquidacao);
        return null;
    });
});
```

Asserções: a lenta lança `OptimisticLockingFailureException`, o valor persistido é o de quem commitou primeiro, e `version` avançou **exatamente uma vez** — provando que a atualização perdida não foi aplicada.

**Corrida real no guard 1:1.** Seis threads chamam `iniciaLiquidacao` na mesma precificação com `trackId` distintos, liberadas por uma largada comum. Resultado: **exatamente 1 sucesso**, 5 falhas, e 1 liquidação no banco. O guard `existsByPrecificacaoId` é *check-then-act* e sozinho não protege — quem garante é a `UNIQUE (precificacao_id)`.

**Constraints como última linha de defesa.** Dois testes inserem via repositório contornando o guard de aplicação: `precificacao_id` duplicado e `track_id` duplicado. Ambos devem levantar `DataIntegrityViolationException` — a proteção existe no banco, independente do código.

**Processamento concorrente da mesma liquidação.** Seis threads chamam `processaLiquidacao` no mesmo id. Ao final, o status é `PROCESSANDO` e `version` é `1`: o guard de estado (só `PENDENTE` avança) somado ao `@Version` impede gravação repetida.

> Estes testes exigiram um detalhe de infraestrutura: `@SpringBootTest` **não** é transacional, então navegar `precificacao.getRecebivel().getCedente()` fora de sessão lança `LazyInitializationException`. Os helpers buscam o cedente por documento em vez de navegar a relação LAZY.

### 6.10 Retry automatico em conflito de versao

`RetryOptimisticLockIT` valida o `@Retryable` nativo do Spring Framework 7. O seam usado e um `@MockitoSpyBean` no `CambioService`, que lanca `OptimisticLockingFailureException` de forma controlada:

```java
doAnswer(invocacao -> {
    if (tentativas.incrementAndGet() == 1) {
        throw new OptimisticLockingFailureException("conflito simulado na primeira tentativa");
    }
    return invocacao.callRealMethod();
}).when(cambioService).converter(any(), any(), any());

liquidacaoService.finalizaLiquidacao(liquidacaoId);

assertThat(tentativas.get()).isEqualTo(2);
```

| Cenario | Asserção |
|---|---|
| Conflito na 1a tentativa | 2 execucoes; status final `LIQUIDADA` |
| Conflito nas 2 primeiras | 3 execucoes; `version = 2` — so o commit bem-sucedido incrementa |
| Conflito permanente | 4 execucoes (1 + 3 retentativas), excecao propaga, status permanece `PROCESSANDO` |
| Falha de negocio (`CambioException`) | **1 execucao** — erro deterministico nao consome retentativas |

> O terceiro cenario e o que prova que **nenhuma tentativa parcial commitou**: apos 4 execucoes falhas, o status continua `PROCESSANDO` e nao ha efeito colateral.
>
> O quarto prova que o `includes` da anotacao esta correto: retentar erro deterministico seria desperdicio.

### 6.11 Client externo

`MockRestServiceServer` verifica o contrato com a Frankfurter sem chamar a rede: os query params enviados (`base`, `quotes`, `date`), array vazio → `CambioException`, corpo nulo, campos desconhecidos ignorados, e — importante — **erro HTTP 500 externo não é `CambioException`**, para que o consumidor o trate como transitório e retente.

> Esta é a classe onde a IA inventou o campo `symbols` em vez de `quotes`. O teste de query params existe exatamente para travar isso.

---

## 7. Como executar

A separação é por **convenção de nome**, não por tag: o surefire roda `*Test.java`, o failsafe roda `*IT.java`.

### Testes rápidos (padrão) — sem infraestrutura

```bash
./mvnw test
```

Roda **179 testes** em segundos. Nenhum sobe banco ou broker.

### Suíte completa — requer MySQL e RabbitMQ

```bash
docker-compose up -d mysql rabbitmq
./mvnw verify
```

Roda os **208 testes** (179 surefire + 29 failsafe) e gera o relatório de cobertura agregado.

### Relatório de cobertura

```bash
./mvnw verify
open target/site/jacoco/index.html
```

O JaCoCo acumula as execuções de surefire e failsafe no mesmo `jacoco.exec` (`prepare-agent` + `prepare-agent-integration` com `append=true`), então o relatório reflete as duas fases.

---

## 8. Cobertura

| Pacote | Instruções | Branches |
|---|---|---|
| `config` | 100% | n/a |
| `controller` | 100% | n/a |
| `enums` | 100% | 100% |
| `messaging` | 100% | 100% |
| `service.strategy` | 100% | n/a |
| `service.impl` | 100% | 94,2% |
| `repository` | 100% | 91,7% |
| `client` | 100% | 100% |
| `mapper` | 95,5% | 50% |
| `exception` | 83,0% | 50% |
| **Total** | **97,9%** | **92,5%** |

Excluídos do relatório: `Application`, `dto/**`, `entity/**` e `OpenApiConfig` — código sem lógica (records, getters do Lombok, bean de configuração). Incluí-los infla a métrica sem dizer nada sobre risco.

### Pontos não cobertos, e por quê

| Lacuna | Motivo |
|---|---|
| `exception` a 83% | Construtores de exceção não exercitados em todas as sobrecargas. Risco nulo. |
| `mapper` branches a 50% | Ramo de `moedaDestino == null` no `LoteMapper` só ocorre em lote mono-moeda persistido; coberto no caminho oposto. |
| `service.impl` branches a 94,2% | Ramos defensivos inalcançáveis pelo fluxo normal — ex.: `transicionar` lançando quando os guards anteriores já barraram. É defesa em profundidade. |
| `repository` branches a 91,7% | Combinações de filtro não exercitadas duas a duas em todas as permutações. |

---

## 9. Boas práticas aplicadas

**Mock só o que é fronteira.** `LiquidacaoServiceImplTest` mocka repositórios e `CambioService`, mas usa o `LiquidacaoMapper` **real** — mapper é lógica pura, mockar seria testar o mock. Mesmo critério em `LoteServiceImplTest` com `LoteMapper`.

**Valores esperados fixados, não recalculados.** O teste não repete a fórmula do código; ele afirma `9308.9520`. Se a fórmula mudar, o teste falha — que é o objetivo.

**Parametrização onde a matriz importa.** A máquina de estados tem 41 testes porque a matriz de transições é a especificação. Escrever isso à mão seria 41 métodos quase idênticos.

**`isEqualByComparingTo` para `BigDecimal`, nunca `isEqualTo`.** `9308.9520` e `9308.952` são iguais em valor e diferentes em `equals`. Usar `isEqualTo` cria testes frágeis que falham por escala.

**`@MockitoSettings(strictness = LENIENT)` só onde há setup compartilhado.** Usado em `LiquidacaoServiceImplTest` e `LoteServiceImplTest`, onde um helper prepara mocks que não todo teste consome. Nos demais, a strictness padrão vale — ela pega stub inútil.

**Fábrica de dados centralizada.** `support/DadosDeTeste` monta entidades válidas com FKs coerentes. Evita 15 blocos de setup divergentes.

**Sem `Thread.sleep`.** Espera de estado assíncrono usa Awaitility com polling e timeout.

**CNPJs com dígito verificador válido.** `Cedente.nrDocumento` usa `@CNPJ`; documento fictício com DV inválido faria o teste falhar por 400 em vez do cenário pretendido.

---

## 10. Decisões e divergências em relação à especificação

Registro o que foi feito diferente do prompt de testes, e por quê.

### 10.1 Testcontainers foi tentado e removido — incompatibilidade de ambiente

A intenção era usar Testcontainers para os testes de banco. Foi implementado, e **falhou por incompatibilidade real**:

```
EnvironmentAndSystemPropertyClientProviderStrategy: failed with exception
BadRequestException (Status 400: {"message":"client version 1.32 is too old.
Minimum supported API version is 1.40, please upgrade your client to a newer version"})
```

O Testcontainers 1.21.3 negocia a **API Docker 1.32**; o daemon do ambiente (Docker 29.x via Colima) exige **mínimo 1.40**. Tentativas com `DOCKER_HOST`, `DOCKER_API_VERSION` e `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` não resolveram — o docker-java embarcado ignora a versão pedida nessa estratégia.

Agravante: o `@ServiceConnection` avalia o container durante o *bootstrap* do contexto Spring, **antes** de qualquer condição JUnit — então nem um `@EnabledIf` consegue pular o teste graciosamente.

**Decisão:** remover a dependência (dependência declarada e não usada é dívida) e apontar os testes de banco para o MySQL do `docker-compose`, via profile `integracao`. Funciona, é reproduzível, e o SQL nativo é validado contra o **mesmo** MySQL 8 de produção.

**Quando voltar ao Testcontainers:** assim que o Testcontainers publicar versão com docker-java compatível com a API 1.40+. O ganho é não depender de infra pré-existente. A troca é localizada: `application-integracao.properties` sai, uma classe base com `@Container` entra.

### 10.2 H2 não foi usado

O prompt pedia H2 para os testes de repositório. Não é viável neste projeto:

- As migrations usam sintaxe MySQL — `ENGINE=InnoDB`, `CHARSET=utf8mb4`, e o `DELETE ... JOIN` da `V3`. O Flyway falharia no H2.
- O `ExtratoRepository` usa **SQL nativo com `LIMIT`**. Testar contra H2 validaria uma tradução, não a query que roda em produção.

Um teste de repositório que passa no H2 e quebra no MySQL é pior que nenhum teste: dá falsa confiança.

### 10.3 Classes citadas no prompt que não existem

| Pedido | Realidade | Onde foi testado |
|---|---|---|
| `RecebivelService` | não existe — recebíveis são criados dentro do `LoteServiceImpl` | `LoteServiceImplTest` |
| `ExtratoService` | não existe — extrato é 2 camadas (controller → repository), por decisão de arquitetura (ADR-006) | `ExtratoRepositoryTest` + `ExtratoControllerTest` |

### 10.4 Testes de carga não entraram na suíte

O prompt pedia cenários de 100, 1.000, 10.000 e 100.000 operações. Não foram implementados como teste automatizado, por três razões:

1. **Não é teste, é medição.** Teste responde "está correto?" com resultado binário. Carga responde "quanto aguenta?" — a saída é uma distribuição de latência, que não vira `assert`.
2. **100.000 operações no `mvn test` inviabilizam o build.** Cada liquidação envolve transação, publicação na fila e consumo. O ciclo de feedback iria de segundos para dezenas de minutos.
3. **A ferramenta é outra.** Carga se mede com JMeter, Gatling ou k6, em ambiente dimensionado e isolado — não no mesmo JVM que roda os testes unitários, disputando CPU com o Maven.

**Encaminhamento proposto:** script k6 versionado em `docs/carga/`, executado fora do pipeline de build, contra ambiente dedicado, medindo p50/p95/p99 de `POST /liquidacoes` e a taxa de drenagem da fila. Nenhum número é afirmado aqui porque nenhuma medição foi feita — ver §23 do README.

### 10.5 `@Data` não é validado

O prompt listava `@Data` entre as validações a testar. `@Data` é anotação do **Lombok**, não do Bean Validation, e o projeto deliberadamente **não a usa em entidades** — ela geraria `equals`/`hashCode` sobre coleções LAZY. As validações testadas são as que existem: `@NotNull`, `@NotBlank`, `@NotEmpty`, `@Positive`, `@Future`, `@AssertTrue` e `@CNPJ`.

---

## 11. Próximos passos

| # | Item | Motivo |
|---|---|---|
| 1 | Retomar Testcontainers | Remove a dependência de infra pré-existente para rodar a suíte |
| 2 | Teste de retry/DLQ automatizado | Hoje verificado manualmente (republicação via painel do RabbitMQ); automatizar exige controlar o broker no teste |
| 3 | Script de carga k6 | Ver §10.4 |
| 4 | Mutation testing (PIT) | Cobertura alta não garante asserção forte; PIT mediria a qualidade real das asserções |
