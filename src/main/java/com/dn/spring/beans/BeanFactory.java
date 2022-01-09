package com.dn.spring.beans;

public interface BeanFactory {
	/**
	 * 获取bean
	 * 
	 * @param name
	 *            bean的名字
	 * @return bean 实例
	 * @throws Exception
	 */
	Object getBean(String name) throws Exception;

	/**
	 * 根据类名获取bean
	 * @param className
	 * @return
	 * @throws Exception
	 */
	Object getBean(Class<?> className) throws Exception;
}
