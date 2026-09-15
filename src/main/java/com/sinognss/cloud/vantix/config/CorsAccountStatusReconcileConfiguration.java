package com.sinognss.cloud.vantix.config;

import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileJob;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusReconcileService;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class CorsAccountStatusReconcileConfiguration {
    @Bean
    static BeanDefinitionRegistryPostProcessor corsAccountStatusReconcileRegistrar() {
        return new BeanDefinitionRegistryPostProcessor() {
            @Override
            public void postProcessBeanDefinitionRegistry(
                    org.springframework.beans.factory.support.BeanDefinitionRegistry registry) {
                // Registration is deferred until all configuration classes have contributed their bean definitions.
            }

            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
                String[] gateways = beanFactory.getBeanNamesForType(
                        CorsAccountStatusGateway.class, false, false);
                if (gateways.length == 0) {
                    return;
                }
                DefaultListableBeanFactory registry = (DefaultListableBeanFactory) beanFactory;
                if (!registry.containsBeanDefinition("accountStatusReconcileService")) {
                    RootBeanDefinition definition = new RootBeanDefinition(AccountStatusReconcileService.class);
                    definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
                    registry.registerBeanDefinition("accountStatusReconcileService", definition);
                }
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "vantix.cors.account-status-sync", name = "enabled",
            havingValue = "true")
    AccountStatusReconcileJob accountStatusReconcileJob(ServiceAccountMapper accountMapper,
                                                        AccountStatusReconcileService reconcileService,
                                                        CorsAccountStatusSyncProperties properties,
                                                        Clock clock) {
        return new AccountStatusReconcileJob(accountMapper, reconcileService, properties, clock);
    }
}
