package com.justfun.common.support.swagger;

import com.fasterxml.classmate.ResolvedType;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.justfun.common.support.swagger.annotations.ApiJsonParam;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.service.Parameter;
import springfox.documentation.service.ResolvedMethodParameter;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.schema.EnumTypeDeterminer;
import springfox.documentation.spi.service.OperationBuilderPlugin;
import springfox.documentation.spi.service.contexts.OperationContext;
import springfox.documentation.spi.service.contexts.ParameterContext;
import springfox.documentation.spring.web.plugins.DocumentationPluginsManager;
import springfox.documentation.spring.web.readers.operation.OperationParameterReader;
import springfox.documentation.spring.web.readers.parameter.ExpansionContext;
import springfox.documentation.spring.web.readers.parameter.ModelAttributeParameterExpander;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Lists.newArrayList;
import static springfox.documentation.schema.Collections.isContainerType;
import static springfox.documentation.schema.Maps.isMapType;
import static springfox.documentation.schema.Types.isBaseType;
import static springfox.documentation.schema.Types.typeNameFor;

/**
 * @author kangarooxin
 */
@Component
@Order
public class OperationParameterReader2 implements OperationBuilderPlugin, BeanPostProcessor {
    private final ModelAttributeParameterExpander expander;
    private final EnumTypeDeterminer enumTypeDeterminer;

    @Autowired
    private DocumentationPluginsManager pluginsManager;

    @Autowired
    public OperationParameterReader2(
            ModelAttributeParameterExpander expander,
            EnumTypeDeterminer enumTypeDeterminer) {
        this.expander = expander;
        this.enumTypeDeterminer = enumTypeDeterminer;
    }

    @Override
    public void apply(OperationContext context) {
        context.operationBuilder().parameters(context.getGlobalOperationParameters());
        context.operationBuilder().parameters(readParameters(context));
    }

    @Override
    public boolean supports(DocumentationType delimiter) {
        return true;
    }

    private List<Parameter> readParameters(final OperationContext context) {

        List<ResolvedMethodParameter> methodParameters = context.getParameters();
        List<Parameter> parameters = newArrayList();

        for (ResolvedMethodParameter methodParameter : methodParameters) {
            ResolvedType alternate = context.alternateFor(methodParameter.getParameterType());
            if (!shouldIgnore(methodParameter, alternate, context.getIgnorableParameterTypes())) {

                ParameterContext parameterContext = new ParameterContext(methodParameter,
                        new ParameterBuilder(),
                        context.getDocumentationContext(),
                        context.getGenericsNamingStrategy(),
                        context);

                if (shouldExpand(methodParameter, alternate)) {
                    parameters.addAll(
                            expander.expand(
                                    new ExpansionContext("", alternate, context)));
                } else {
                    parameters.add(pluginsManager.parameter(parameterContext));
                }
            }
        }
        return FluentIterable.from(parameters).filter(not(hiddenParams())).toList();
    }

    private Predicate<Parameter> hiddenParams() {
        return new Predicate<Parameter>() {
            @Override
            public boolean apply(Parameter input) {
                return input.isHidden();
            }
        };
    }

    private boolean shouldIgnore(
            final ResolvedMethodParameter parameter,
            ResolvedType resolvedParameterType,
            final Set<Class> ignorableParamTypes) {

        if (ignorableParamTypes.contains(resolvedParameterType.getErasedType())) {
            return true;
        }
        return FluentIterable.from(ignorableParamTypes)
                .filter(isAnnotation())
                .filter(parameterIsAnnotatedWithIt(parameter)).size() > 0;

    }

    private Predicate<Class> parameterIsAnnotatedWithIt(final ResolvedMethodParameter parameter) {
        return new Predicate<Class>() {
            @Override
            public boolean apply(Class input) {
                return parameter.hasParameterAnnotation(input);
            }
        };
    }

    private Predicate<Class> isAnnotation() {
        return new Predicate<Class>() {
            @Override
            public boolean apply(Class input) {
                return Annotation.class.isAssignableFrom(input);
            }
        };
    }

    private boolean shouldExpand(final ResolvedMethodParameter parameter, ResolvedType resolvedParamType) {
        return !parameter.hasParameterAnnotation(RequestBody.class)
                && !parameter.hasParameterAnnotation(RequestPart.class)
                && !parameter.hasParameterAnnotation(RequestParam.class)
                && !parameter.hasParameterAnnotation(PathVariable.class)
                && !parameter.hasParameterAnnotation(ApiJsonParam.class)
                && !isBaseType(typeNameFor(resolvedParamType.getErasedType()))
                && !enumTypeDeterminer.isEnum(resolvedParamType.getErasedType())
                && !isContainerType(resolvedParamType)
                && !isMapType(resolvedParamType);

    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if(bean instanceof OperationParameterReader) {
            return null;
        }
        return bean;
    }
}
