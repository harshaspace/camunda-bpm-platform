/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.impl.el;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.dmn.engine.impl.spi.el.ElProvider;
import org.camunda.bpm.engine.delegate.VariableScope;
import org.camunda.bpm.engine.impl.core.variable.scope.AbstractVariableScope;
import org.camunda.bpm.engine.impl.dmn.el.ProcessEngineJuelElProvider;
import org.camunda.bpm.engine.impl.javax.el.ArrayELResolver;
import org.camunda.bpm.engine.impl.javax.el.CompositeELResolver;
import org.camunda.bpm.engine.impl.javax.el.ELContext;
import org.camunda.bpm.engine.impl.javax.el.ELResolver;
import org.camunda.bpm.engine.impl.javax.el.ExpressionFactory;
import org.camunda.bpm.engine.impl.javax.el.FunctionMapper;
import org.camunda.bpm.engine.impl.javax.el.ListELResolver;
import org.camunda.bpm.engine.impl.javax.el.MapELResolver;
import org.camunda.bpm.engine.impl.javax.el.ValueExpression;
import org.camunda.bpm.engine.impl.juel.ExpressionFactoryImpl;
import org.camunda.bpm.engine.impl.util.EnsureUtil;
import org.camunda.bpm.engine.test.mock.MockElResolver;
import org.camunda.bpm.engine.variable.context.VariableContext;

/**
 * JUEL-specific implementation of an {@link ExpressionManager}.
 *
 * @author Tom Baeyens
 * @author Dave Syer
 * @author Frederik Heremans
 */
public class JuelExpressionManager implements ExpressionManager, ElProviderCompatible {

  protected List<FunctionMapper> functionMappers = new ArrayList<>();
  protected ExpressionFactory expressionFactory;
  // Default implementation (does nothing)
  protected ELContext parsingElContext = new ProcessEngineElContext(functionMappers);
  protected Map<Object, Object> beans;
  protected volatile ELResolver elResolver; // why volatile? =>
                                            // https://jira.camunda.com/browse/CAM-12106
  protected volatile ElProvider elProvider;

  public JuelExpressionManager() {
    this(null);
  }

  public JuelExpressionManager(Map<Object, Object> beans) {
    // Use the ExpressionFactoryImpl built-in version of juel, with parametrised
    // method expressions enabled
    expressionFactory = new ExpressionFactoryImpl();
    this.beans = beans;
  }

  @Override
  public Expression createExpression(String expression) {
    ValueExpression valueExpression = createValueExpression(expression);
    return new JuelExpression(valueExpression, this, expression);
  }

  public ValueExpression createValueExpression(String expression) {
    return expressionFactory.createValueExpression(parsingElContext, expression, Object.class);
  }

  public void setExpressionFactory(ExpressionFactory expressionFactory) {
    this.expressionFactory = expressionFactory;
  }

  public ELContext getElContext(VariableScope variableScope) {
    ELContext elContext = null;
    if (variableScope instanceof AbstractVariableScope) {
      AbstractVariableScope variableScopeImpl = (AbstractVariableScope) variableScope;
      elContext = variableScopeImpl.getCachedElContext();
    }

    if (elContext == null) {
      elContext = createElContext(variableScope);
      if (variableScope instanceof AbstractVariableScope) {
        ((AbstractVariableScope) variableScope).setCachedElContext(elContext);
      }
    }

    return elContext;
  }

  public ELContext createElContext(VariableContext variableContext) {
    ELResolver elResolver = getCachedElResolver();
    ProcessEngineElContext elContext = new ProcessEngineElContext(functionMappers, elResolver);
    elContext.putContext(ExpressionFactory.class, expressionFactory);
    elContext.putContext(VariableContext.class, variableContext);
    return elContext;
  }

  protected ProcessEngineElContext createElContext(VariableScope variableScope) {
    ELResolver elResolver = getCachedElResolver();
    ProcessEngineElContext elContext = new ProcessEngineElContext(functionMappers, elResolver);
    elContext.putContext(ExpressionFactory.class, expressionFactory);
    elContext.putContext(VariableScope.class, variableScope);
    return elContext;
  }

  protected ELResolver getCachedElResolver() {
    if (elResolver == null) {
      synchronized (this) {
        if (elResolver == null) {
          elResolver = createElResolver();
        }
      }
    }

    return elResolver;
  }

  protected ELResolver createElResolver() {
    CompositeELResolver elResolver = new CompositeELResolver();
    elResolver.add(new VariableScopeElResolver());
    elResolver.add(new VariableContextElResolver());
    elResolver.add(new MockElResolver());

    if (beans != null) {
      // ACT-1102: Also expose all beans in configuration when using standalone
      // engine, not
      // in spring-context
      elResolver.add(new ReadOnlyMapELResolver(beans));
    }

    elResolver.add(new ProcessApplicationElResolverDelegate());

    elResolver.add(new ArrayELResolver());
    elResolver.add(new ListELResolver());
    elResolver.add(new MapELResolver());
    elResolver.add(new ProcessApplicationBeanElResolverDelegate());

    return elResolver;
  }

  /**
   * @param elFunctionMapper
   */
  @Override
  public void addFunction(String name, Method function) {
    EnsureUtil.ensureNotEmpty("name", name);
    this.functionMappers.add(new FunctionMapper() {

      @Override
      public Method resolveFunction(String prefix, String localName) {
        if (name.equals(localName)) {
          return function;
        }
        return null;
      }
    });
  }

  @Override
  public ElProvider toElProvider() {
    if (elProvider == null) {
      synchronized (this) {
        if (elProvider == null) {
          elProvider = createElProvider();
        }
      }
    }
    return elProvider;
  }

  protected ElProvider createElProvider() {
    return new ProcessEngineJuelElProvider(this);
  }
}
