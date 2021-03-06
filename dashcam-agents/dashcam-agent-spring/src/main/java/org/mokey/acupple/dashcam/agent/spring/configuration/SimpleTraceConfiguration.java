package org.mokey.acupple.dashcam.agent.spring.configuration;

import org.mokey.acupple.dashcam.agent.spring.SpringTraceAopInterceptor;
import org.mokey.acupple.dashcam.agent.spring.annotations.EnableTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Forest on 2015/12/15.
 */
@Configuration
public class SimpleTraceConfiguration implements ImportAware, EnvironmentAware {
    private static boolean pretty = false;

    private static final Logger log = LoggerFactory.getLogger(SimpleTraceConfiguration.class);

    private static final String CONTROLLER_ADVICE_CLASS_NAME = "org.springframework.web.bind.annotation.ControllerAdvice";
    private static final String CONFIG_ADVICE_CLASS_NAME = "org.springframework.context.annotation.Configuration";

    protected AnnotationAttributes annotationAttributes;

    protected String configClassName;

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        this.configClassName = importMetadata.getClassName();
        this.annotationAttributes = AnnotationAttributes.fromMap(importMetadata.getAnnotationAttributes(
                EnableTrace.class.getName(), false));
        Assert.notNull(this.annotationAttributes, "@EnableTrace is not present on importing class " + importMetadata.getClassName());
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public Advisor packageTraceAdvisor() {
        ComposablePointcut resultPointcut = new ComposablePointcut();
        {
            pretty = this.annotationAttributes.getBoolean("pretty");
            List<String> basePackages = findBasePackages();
            String pointcutExpression = makeExpression(basePackages);
            AspectJExpressionPointcut packagePointcut = new AspectJExpressionPointcut();
            log.info("Include package Pointcut expression : {}", pointcutExpression);
            packagePointcut.setExpression(pointcutExpression);
            resultPointcut.intersection((Pointcut) packagePointcut);
        }

        String excludeAnnotation = buildExcludeAnnotation();
        log.info("Exclude Annotation Pointcut expression : {}", excludeAnnotation);

        AspectJExpressionPointcut basePointcut = new AspectJExpressionPointcut();
        basePointcut.setExpression(excludeAnnotation);
        resultPointcut.intersection((Pointcut) basePointcut);

        DefaultPointcutAdvisor pointcutAdvisor = new DefaultPointcutAdvisor(resultPointcut, new SpringTraceAopInterceptor());
        pointcutAdvisor.setOrder(Integer.MAX_VALUE);
        return pointcutAdvisor;
    }

    String buildExcludeAnnotation() {

        List<String> excludeList = new ArrayList<String>();
        if(hasAnnotation(CONFIG_ADVICE_CLASS_NAME)){
            excludeList.add(CONFIG_ADVICE_CLASS_NAME);
        }
        if(hasAnnotation(CONTROLLER_ADVICE_CLASS_NAME)){
            excludeList.add(CONTROLLER_ADVICE_CLASS_NAME);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < excludeList.size(); i++) {

            String annotationName = excludeList.get(i);
            if (i > 0) {
                sb.append(" and ");
            }

            sb.append("!@within(").append(annotationName).append(")");
        }
        return sb.toString();
    }


    private boolean hasAnnotation(String annotationName) {
        try {
            Class<?> aClass = Class.forName(annotationName);
            if(aClass.isAnnotation()){
                return true;
            }
        } catch (ClassNotFoundException e) {
        }
        return false;
    }


    /**
     * Find base packages.
     */
    private List<String> findBasePackages() {

        List<String> basePackages = new ArrayList<String>();
        for (String pkg : this.annotationAttributes.getStringArray("value")) {
            if (StringUtils.hasText(pkg)) {
                basePackages.add(pkg);
            }
        }
        for (String pkg : this.annotationAttributes.getStringArray("basePackages")) {
            if (StringUtils.hasText(pkg)) {
                basePackages.add(pkg);
            }
        }
        for (Class<?> clazz : this.annotationAttributes.getClassArray("basePackageClasses")) {
            basePackages.add(ClassUtils.getPackageName(clazz));
        }
        if (basePackages.isEmpty()) {
            String defaultPackageName = ClassUtils.getPackageName(configClassName);
            basePackages.add(defaultPackageName);
            log.debug("Default package name : {}", defaultPackageName);
        }
        return basePackages;
    }

    private String makeExpression(List<String> basePackages) {
        Assert.notNull(basePackages);
        StringBuilder sb = new StringBuilder();
        {
            sb.append("(");
            int cnt = 0;
            for (String basePackage : basePackages) {
                if (!StringUtils.hasText(basePackage)) {
                    continue;
                }
                cnt++;
                sb.append(String.format("execution(* %s..*.*(..))", basePackage));
                if (basePackages.size() != cnt) {
                    sb.append(" or ");
                } else {
                    sb.append(") ");
                }
            }
        }
        return sb.toString();
    }

    public static boolean isPretty(){
        return pretty;
    }
}
