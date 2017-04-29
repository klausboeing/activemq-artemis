package org.apache.artemis.client.cdi.extension;

import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.artemis.client.cdi.configuration.ArtemisClientConfiguration;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.jms.JMSContext;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.emptySet;

public class JMSContextBean implements Bean<JMSContext> {
    private BeanManager beanManager;

    private ConnectionFactoryBean connectionFactoryBean;

    public JMSContextBean(BeanManager beanManager, ConnectionFactoryBean connectionFactoryBean) {
        this.beanManager = beanManager;
        this.connectionFactoryBean = connectionFactoryBean;
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
    public JMSContext create(CreationalContext<JMSContext> creationalContext) {
        ActiveMQConnectionFactory connectionFactory = (ActiveMQConnectionFactory) beanManager.getReference(connectionFactoryBean, ActiveMQConnectionFactory.class, creationalContext);

        return connectionFactory.createContext();
    }

    @Override
    public void destroy(JMSContext jmsContext, CreationalContext<JMSContext> creationalContext) {
        jmsContext.close();
    }

    @Override
    public Set<Type> getTypes() {
        Set<Type> types = new HashSet<>();
        types.add(JMSContext.class);
        return types;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        Set<Annotation> qualifiers = new HashSet<>();
        qualifiers.add(AnyLiteral.INSTANCE);
        qualifiers.add(DefaultLiteral.INSTANCE);
        qualifiers.addAll(connectionFactoryBean.getQualifiers());
        return qualifiers;

    }

    @Override
    public Class<? extends Annotation> getScope() {
        return Dependent.class;
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
