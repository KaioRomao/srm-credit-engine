package br.com.srm.credit.engine.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;

import br.com.srm.credit.engine.dto.message.LiquidacaoMensagem;
import br.com.srm.credit.engine.service.LiquidacaoService;

import tools.jackson.databind.ObjectMapper;

public class LiquidacaoMessageRecoverer extends RepublishMessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(LiquidacaoMessageRecoverer.class);

    private final LiquidacaoService liquidacaoService;
    private final ObjectMapper objectMapper;

    public LiquidacaoMessageRecoverer(
            RabbitTemplate rabbitTemplate,
            String deadLetterExchange,
            String deadLetterRoutingKey,
            LiquidacaoService liquidacaoService,
            ObjectMapper objectMapper) {
        super(rabbitTemplate, deadLetterExchange, deadLetterRoutingKey);
        this.liquidacaoService = liquidacaoService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        try {
            LiquidacaoMensagem mensagem = objectMapper.readValue(message.getBody(), LiquidacaoMensagem.class);
            liquidacaoService.registrarFalha(mensagem.liquidacaoId(), cause);
        } catch (Exception e) {
            log.error("Não foi possível registrar FALHA para a mensagem descartada; segue para a DLQ", e);
        }

        log.error("Tentativas esgotadas; enviando mensagem para a DLQ. Causa: {}", cause.getMessage());
        super.recover(message, cause);
    }
}
