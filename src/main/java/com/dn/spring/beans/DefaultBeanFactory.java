package com.dn.spring.beans;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.CollectionUtils;

import java.io.Closeable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultBeanFactory implements BeanFactory, BeanDefinitionRegistry, Closeable {

    private final Log logger = LogFactory.getLog(getClass());

    private Map<String, BeanDefinition> beanDefintionMap = new ConcurrentHashMap<>(256);

    private Map<Class, BeanDefinition> classDefintionMap = new ConcurrentHashMap<>(256);

    private Map<String, Object> beanMap = new ConcurrentHashMap<>(256);
    /**
     * 类型---->bean实体
     */
    private Map<Class, Object> classBeanMap = new ConcurrentHashMap<>(256);
    /**
     * 线程安全
     */
    private ThreadLocal<Set<String>> buildingBeans = new ThreadLocal<>();

    @Override
    public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition)
            throws BeanDefinitionRegistException {
        Objects.requireNonNull(beanName, "注册bean需要给入beanName");
        Objects.requireNonNull(beanDefinition, "注册bean需要给入beanDefinition");

        // 校验给入的bean是否合法
        if (!beanDefinition.validate()) {
            throw new BeanDefinitionRegistException("名字为[" + beanName + "] 的bean定义不合法：" + beanDefinition);
        }

        if (this.containsBeanDefinition(beanName)) {
            throw new BeanDefinitionRegistException(
                    "名字为[" + beanName + "] 的bean定义已存在:" + this.getBeanDefinition(beanName));
        }

        this.beanDefintionMap.put(beanName, beanDefinition);
    }

    @Override
    public BeanDefinition getBeanDefinition(String beanName) {
        return this.beanDefintionMap.get(beanName);
    }

    @Override
    public boolean containsBeanDefinition(String beanName) {

        return this.beanDefintionMap.containsKey(beanName);
    }

    @Override
    public Object getBean(String name) throws Exception {
        return this.doGetBean(name);
    }

    protected Object doGetBean(String beanName) throws Exception {
        Objects.requireNonNull(beanName, "beanName不能为空");

        Object instance = beanMap.get(beanName);

        if (instance != null) {
            return instance;
        }

        BeanDefinition bd = this.getBeanDefinition(beanName);
        Objects.requireNonNull(bd, "不存在name为：" + beanName + "beean 定义！");

        // 记录正在创建的Bean
        Set<String> ingBeans = this.buildingBeans.get();
        if (ingBeans == null) {
            ingBeans = new HashSet<>();
            this.buildingBeans.set(ingBeans);
        }

        // 检测循环依赖
        /**
         * 这个集合里面是否存在这个bean
         */
        if (ingBeans.contains(beanName)) {
            throw new Exception(beanName + " 循环依赖！" + ingBeans);
        }

        // 记录正在创建的Bean
        ingBeans.add(beanName);

        Class<?> type = bd.getBeanClass();
        instance = getBean(bd, beanName, type, null, ingBeans);
        return instance;
    }


    private void setClassBeanMapAndClassDefintionMap(Object instance, Class className, BeanDefinition bd) {
        if (className == null) {
            className = instance.getClass();
        }
        if (!classBeanMap.containsKey(className)) {
            classBeanMap.put(className, instance);
        }
        if (!classDefintionMap.containsKey(className)) {
            classDefintionMap.put(className, bd);
        }
    }


    private Object getValue(Object rv) throws Exception {
        Object v = null;
        if (rv == null) {
            v = null;
        } else if (rv instanceof BeanReference) {
            v = this.doGetBean(((BeanReference) rv).getBeanName());
        } else if (rv instanceof Object[]) {
            // TODO 处理集合中的bean引用
        } else if (rv instanceof Collection) {
            // TODO 处理集合中的bean引用
        } else if (rv instanceof Properties) {
            // TODO 处理properties中的bean引用
        } else if (rv instanceof Map) {
            // TODO 处理Map中的bean引用
        } else {
            v = rv;
        }
        return v;
    }

    private void setPropertyDIValues(BeanDefinition bd, Object instance) throws Exception {
        //判断是否有属性依赖
        if (CollectionUtils.isEmpty(bd.getPropertyValues())) {
            return;
        }
        //遍历属性
        for (PropertyValue pv : bd.getPropertyValues()) {
            //如果属性依赖没有指定名称 忽略
            if (StringUtils.isBlank(pv.getName())) {
                continue;
            }
            //获取属性类型
            Class<?> clazz = instance.getClass();
            //获取对应的字段
            Field p = clazz.getDeclaredField(pv.getName());
            //将访问类型设置为可以访问的
            p.setAccessible(true);
            //获取对应得值
            Object rv = pv.getValue();
            Object v = getValue(rv);
            //通过反射将值设置进去
            p.set(instance, v);

        }
    }

    // 构造方法来构造对象
    private Object createInstanceByConstructor(BeanDefinition bd) throws Exception {
        try {
            Object[] args = this.getConstructorArgumentValues(bd);
            if (args == null) {
                return bd.getBeanClass().newInstance();
            } else {
                // 决定构造方法
                return this.determineConstructor(bd, args).newInstance(args);
            }
        } catch (SecurityException e1) {
            logger.error("创建bean的实例异常,beanDefinition：" + bd, e1);
            throw e1;
        }
    }

    private Object[] getConstructorArgumentValues(BeanDefinition bd) throws Exception {

        return this.getRealValues(bd.getConstructorArgumentValues());

    }

    private Object[] getRealValues(List<?> defs) throws Exception {
        if (CollectionUtils.isEmpty(defs)) {
            return null;
        }
        Object[] values = new Object[defs.size()];
        int i = 0;
        for (Object rv : defs) {
            values[i++] = getValue(rv);
        }

        return values;
    }

    /**
     * 查找构造方法
     *
     * @param bd
     * @param args
     * @return
     * @throws Exception
     */
    private Constructor<?> determineConstructor(BeanDefinition bd, Object[] args) throws Exception {

        Constructor<?> ct;

        if (args == null) {
            return bd.getBeanClass().getConstructor(null);
        }

        // 对于原型bean,从第二次开始获取bean实例时，可直接获得第一次缓存的构造方法。
        ct = bd.getConstructor();
        if (ct != null) {
            return ct;
        }

        // 根据参数类型获取精确匹配的构造方法
        Class<?>[] paramTypes = new Class[args.length];
        int j = 0;
        /**
         * 获取所有参数类型
         */
        for (Object p : args) {
            paramTypes[j++] = p.getClass();
        }
        try {
            ct = bd.getBeanClass().getConstructor(paramTypes);
        } catch (Exception e) {
            // 这个异常不需要处理
        }

        if (ct == null) {

            // 没有精确参数类型匹配的，则遍历匹配所有的构造方法
            // 判断逻辑：先判断参数数量，再依次比对形参类型与实参类型
            outer:
            for (Constructor<?> ct0 : bd.getBeanClass().getConstructors()) {
                Class<?>[] paramterTypes = ct0.getParameterTypes();
                if (paramterTypes.length == args.length) {
                    for (int i = 0; i < paramterTypes.length; i++) {
                        /**
                         * 判断第一个参数是否可以赋值给构造参数中的第一个参数
                         */
                        if (!paramterTypes[i].isAssignableFrom(args[i].getClass())) {
                            continue outer;
                        }
                    }
                    ct = ct0;
                    break outer;
                }
            }
        }

        if (ct != null) {
            // 对于原型bean,可以缓存找到的构造方法，方便下次构造实例对象。在BeanDefinfition中获取设置所用构造方法的方法。
            // 同时在上面增加从beanDefinition中获取的逻辑。
            if (bd.isPrototype()) {
                bd.setConstructor(ct);
            }
            return ct;
        } else {
            throw new Exception("不存在对应的构造方法！" + bd);
        }
    }

    /**
     * 查找工厂方法
     *
     * @param bd
     * @param args
     * @param type
     * @return
     * @throws Exception
     */
    private Method determineFactoryMethod(BeanDefinition bd, Object[] args, Class<?> type) throws Exception {
        if (type == null) {
            type = bd.getBeanClass();
        }

        String methodName = bd.getFactoryMethodName();

        if (args == null) {
            return type.getMethod(methodName, null);
        }

        Method m;
        // 对于原型bean,从第二次开始获取bean实例时，可直接获得第一次缓存的构造方法。
        m = bd.getFactoryMethod();
        if (m != null) {
            return m;
        }

        /**
         * 根据参数类型获取精确匹配的方法
         */
        Class[] paramTypes = new Class[args.length];
        int j = 0;
        for (Object p : args) {
            paramTypes[j++] = p.getClass();
        }
        try {
            m = type.getMethod(methodName, paramTypes);
        } catch (Exception e) {
            // 这个异常不需要处理
        }

        if (m == null) {

            // 没有精确参数类型匹配的，则遍历匹配所有的方法
            // 判断逻辑：先判断参数数量，再依次比对形参类型与实参类型
            outer:
            for (Method m0 : type.getMethods()) {
                if (!m0.getName().equals(methodName)) {
                    continue;
                }
                Class<?>[] paramterTypes = m.getParameterTypes();
                if (paramterTypes.length == args.length) {
                    for (int i = 0; i < paramterTypes.length; i++) {
                        if (!paramterTypes[i].isAssignableFrom(args[i].getClass())) {
                            continue outer;
                        }
                    }

                    m = m0;
                    break;
                }
            }
        }

        if (m != null) {
            // 对于原型bean,可以缓存找到的方法，方便下次构造实例对象。在BeanDefinfition中获取设置所用方法的方法。
            // 同时在上面增加从beanDefinition中获取的逻辑。
            if (bd.isPrototype()) {
                bd.setFactoryMethod(m);
            }
            return m;
        } else {
            throw new Exception("不存在对应的构造方法！" + bd);
        }
    }

    // 静态工厂方法
    private Object createInstanceByStaticFactoryMethod(BeanDefinition bd) throws Exception {

        Class<?> type = bd.getBeanClass();
        Object[] realArgs = this.getRealValues(bd.getConstructorArgumentValues());
        Method m = this.determineFactoryMethod(bd, realArgs, null);
        return m.invoke(type, realArgs);
    }

    // 工厂bean方式来构造对象
    private Object createInstanceByFactoryBean(BeanDefinition bd) throws Exception {

        Object factoryBean = this.doGetBean(bd.getFactoryBeanName());
        Object[] realArgs = this.getRealValues(bd.getConstructorArgumentValues());
        Method m = this.determineFactoryMethod(bd, realArgs, factoryBean.getClass());

        return m.invoke(factoryBean, realArgs);
    }

    /**
     * 执行初始化方法
     *
     * @param bd
     * @param instance
     * @throws Exception
     */
    private void doInit(BeanDefinition bd, Object instance) throws Exception {
        // 执行初始化方法
        if (StringUtils.isNotBlank(bd.getInitMethodName())) {
            Method m = instance.getClass().getMethod(bd.getInitMethodName(), null);
            m.invoke(instance, null);
        }
    }

    @Override
    public void close() {
        // 执行单例实例的销毁方法
        for (Entry<String, BeanDefinition> e : this.beanDefintionMap.entrySet()) {
            String beanName = e.getKey();
            BeanDefinition bd = e.getValue();

            if (bd.isSingleton() && StringUtils.isNotBlank(bd.getDestroyMethodName())) {
                Object instance = this.beanMap.get(beanName);
                try {
                    Method m = instance.getClass().getMethod(bd.getDestroyMethodName(), null);
                    m.invoke(instance, null);
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException e1) {
                    logger.error("执行bean[" + beanName + "] " + bd + " 的 销毁方法异常！", e1);
                }
            }
        }
    }

    @Override
    public Object getBean(Class<?> className) throws Exception {
        return this.doGetBean(className);
    }

    protected Object doGetBean(Class<?> className) throws Exception {
        Objects.requireNonNull(className, "类名不能为空");
        Object instance = classBeanMap.get(className);
        if (instance != null) {
            return instance;
        }
        if (!classDefintionMap.containsKey(className)) {
            Objects.requireNonNull("不存在class为：" + className + "bean 定义！");
        }
        BeanDefinition beanDefinition = classDefintionMap.get(className);

        Class classB = beanDefinition.getBeanClass();
        if (classB != null && classB != className) {
            throw new Exception("指定的class类型和bean定义中的类型不相同");
        }
        // 记录正在创建的Bean
        Set<String> ingBeans = this.buildingBeans.get();
        if (ingBeans == null) {
            ingBeans = new HashSet<>();
            this.buildingBeans.set(ingBeans);
        }

        String beanName = classB.getSimpleName().trim();
        beanName = Character.toLowerCase(beanName.charAt(0)) + "" + beanName.substring(1);

        // 检测循环依赖
        /**
         * 这个集合里面是否存在这个bean
         */
        if (ingBeans.contains(beanName)) {
            throw new Exception(beanName + " 循环依赖！" + ingBeans);
        }
        // 记录正在创建的Bean
        ingBeans.add(beanName);
        instance = getBean(beanDefinition, beanName, classB, className, ingBeans);
        return instance;
    }


    private Object getBean(BeanDefinition beanDefinition, String beanName, Class classB, Class className, Set<String> ingBeans) throws Exception {
        Object instance;
        if (classB != null) {
            if (StringUtils.isBlank(beanDefinition.getFactoryMethodName())) {
                // 构造方法来构造对象
                instance = this.createInstanceByConstructor(beanDefinition);
            } else {
                // 静态工厂方法
                instance = this.createInstanceByStaticFactoryMethod(beanDefinition);
            }
        } else {
            // 工厂bean方式来构造对象
            instance = this.createInstanceByFactoryBean(beanDefinition);
        }

        // 创建好实例后，移除创建中记录
        ingBeans.remove(beanName);

        // 给入属性依赖
        this.setPropertyDIValues(beanDefinition, instance);
        // 执行初始化方法
        this.doInit(beanDefinition, instance);
        if (beanDefinition.isSingleton()) {
            beanMap.put(beanName, instance);
            setClassBeanMapAndClassDefintionMap(instance, className, beanDefinition);
        }
        return instance;
    }

    @Override
    public void registerBeanDefinition(Class className, BeanDefinition beanDefinition) throws BeanDefinitionRegistException {

    }

    @Override
    public BeanDefinition getBeanDefinition(Class className) {
        return null;
    }

    @Override
    public boolean containsBeanDefinition(Class className) {
        return false;
    }
}
