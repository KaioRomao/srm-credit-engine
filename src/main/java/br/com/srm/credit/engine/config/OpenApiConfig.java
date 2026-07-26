package br.com.srm.credit.engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI srmCreditEngineOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("SRM Credit Engine API")
                                .version("v1")
                                .description(
                                        """
                        Motor de crédito para antecipação de recebíveis: precificação por Strategy \
                        (deságio com spread por tipo de título), câmbio via Frankfurter, entrada de \
                        lote e liquidação idempotente processada de forma assíncrona.

                        Erros seguem um corpo único — `timestamp`, `status`, `error`, `message`, \
                        `path` — e `erros[]` com detalhe por campo nas falhas de validação."""));
    }
}
