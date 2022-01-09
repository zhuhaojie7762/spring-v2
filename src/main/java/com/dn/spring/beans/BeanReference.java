package com.dn.spring.beans;

/**
 * 用于依赖注入中描述bean依赖
 *

 */
public class BeanReference {

	private String beanName;

	public BeanReference(String beanName) {
		super();
		this.beanName = beanName;
	}

	/**
	 * 获得引用的beanName
	 *
	 * @return
	 */
	public String getBeanName() {
		return this.beanName;
	}
}
