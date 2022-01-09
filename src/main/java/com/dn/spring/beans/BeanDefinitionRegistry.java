package com.dn.spring.beans;

public interface BeanDefinitionRegistry {

    void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) throws BeanDefinitionRegistException;

    BeanDefinition getBeanDefinition(String beanName);

    boolean containsBeanDefinition(String beanName);

    void registerBeanDefinition(Class className, BeanDefinition beanDefinition) throws BeanDefinitionRegistException;

    BeanDefinition getBeanDefinition(Class className);

    boolean containsBeanDefinition(Class className);

}
