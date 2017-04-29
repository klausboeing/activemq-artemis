/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.artemis.client.cdi.extension;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.server.impl.JMSServerManagerImpl;
import org.apache.artemis.client.cdi.configuration.ArtemisClientConfiguration;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.jms.ConnectionFactory;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;

public class ConnectionFactoryBean implements Bean<ActiveMQConnectionFactory> {

    private BeanManager beanManager;

    private Bean<ArtemisClientConfiguration> configurationBean;

    public ConnectionFactoryBean(BeanManager beanManager, Bean<ArtemisClientConfiguration> configurationBean) {
        this.beanManager = beanManager;
        this.configurationBean = configurationBean;
    }

    @Override
    public Class<?> getBeanClass() {
        return ConfigurationImpl.class;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return emptySet();
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public ActiveMQConnectionFactory create(CreationalContext<ActiveMQConnectionFactory> creationalContext) {
        Configuration embeddedConfiguration = (Configuration) beanManager.getReference(beanManager.getBeans(Configuration.class).iterator().next(), Configuration.class, creationalContext);
        ArtemisClientConfiguration configuration = (ArtemisClientConfiguration) beanManager.getReference(configurationBean, ArtemisClientConfiguration.class, creationalContext);

        if (configuration.startEmbeddedBroker()) {
            try {
                ActiveMQServer activeMQServer = ActiveMQServers.newActiveMQServer(embeddedConfiguration, false);
                JMSServerManagerImpl jmsServerManager = new JMSServerManagerImpl(activeMQServer);
                jmsServerManager.start();
            } catch (Exception e) {
                throw new RuntimeException("Unable to start embedded JMS", e);
            }
        }

        try {
            return createConnectionFactory(configuration);
        } catch (Exception e) {
            throw new RuntimeException("Unable to connect to remote server", e);
        }
    }

    private ActiveMQConnectionFactory createConnectionFactory(ArtemisClientConfiguration configuration) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, "1");
        final ActiveMQConnectionFactory activeMQConnectionFactory;
        if (configuration.getUrl() != null) {
            activeMQConnectionFactory = ActiveMQJMSClient.createConnectionFactory(configuration.getUrl(), null);
        } else {
            if (configuration.getHost() != null) {
                params.put(TransportConstants.HOST_PROP_NAME, configuration.getHost());
                params.put(TransportConstants.PORT_PROP_NAME, configuration.getPort());
            }
            if (configuration.isHa()) {
                activeMQConnectionFactory = ActiveMQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, new TransportConfiguration(configuration.getConnectorFactory(), params));
            } else {
                activeMQConnectionFactory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(configuration.getConnectorFactory(), params));
            }
        }
        if (configuration.hasAuthentication()) {
            activeMQConnectionFactory.setUser(configuration.getUsername());
            activeMQConnectionFactory.setPassword(configuration.getPassword());
        }
        // The CF will probably be GCed since it was injected, so we disable the finalize check
        return activeMQConnectionFactory;
    }

    @Override
    public void destroy(ActiveMQConnectionFactory connectionFactory, CreationalContext<ActiveMQConnectionFactory> creationalContext) {
        connectionFactory.close();
    }

    @Override
    public Set<Type> getTypes() {
        Set<Type> types = new HashSet<>();
        types.add(ActiveMQConnectionFactory.class);
        types.add(ConnectionFactory.class);
        return types;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        Set<Annotation> qualifiers = new HashSet<>();
        qualifiers.add(AnyLiteral.INSTANCE);
        qualifiers.add(DefaultLiteral.INSTANCE);
        qualifiers.addAll(configurationBean.getQualifiers());
        return qualifiers;

    }

    @Override
    public Class<? extends Annotation> getScope() {
        return ApplicationScoped.class;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return emptySet();
    }

    @Override
    public boolean isAlternative() {
        return false;
    }
}
