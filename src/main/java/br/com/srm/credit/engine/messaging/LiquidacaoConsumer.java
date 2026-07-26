package br.com.srm.credit.engine.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import br.com.srm.credit.engine.dto.message.LiquidacaoMensagem;
import br.com.srm.credit.engine.exception.ErroDeNegocio;
import br.com.srm.credit.engine.service.LiquidacaoService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LiquidacaoConsumer {

    private static final Logger log = LoggerFactory.getLogger(LiquidacaoConsumer.class);

    private final LiquidacaoService liquidacaoService;

    @RabbitListener(queues = "${app.rabbitmq.liquidacao.queue}")
    public void consumir(LiquidacaoMensagem mensagem) {
        Long liquidacaoId = mensagem.liquidacaoId();
        log.info("Recebida liquidacao {} (trackId={})", liquidacaoId, mensagem.trackId());
        try {
            liquidacaoService.processaLiquidacao(liquidacaoId);
            liquidacaoService.finalizaLiquidacao(liquidacaoId);
            log.info("Liquidacao {} concluida com sucesso", liquidacaoId);
        } catch (RuntimeException e) {
            if (e instanceof ErroDeNegocio) {
                log.warn("Falha de negocio na liquidacao {}: {}", liquidacaoId, e.getMessage());
                liquidacaoService.registrarFalha(liquidacaoId, e);
            } else {
                log.error("Falha possivelmente transitoria na liquidacao {}; deixando o retry agir", liquidacaoId, e);
                throw e;
            }
        }
    }
}
