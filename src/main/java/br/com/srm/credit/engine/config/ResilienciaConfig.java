package br.com.srm.credit.engine.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.resilience.annotation.EnableResilientMethods;

@Configuration
@EnableResilientMethods(order = Ordered.LOWEST_PRECEDENCE - 1)
public class ResilienciaConfig {}
