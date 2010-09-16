/* 
 * $Id$
 * $URL$
 *
 * ====================================================================
 * Ikasan Enterprise Integration Platform
 * 
 * Distributed under the Modified BSD License.
 * Copyright notice: The copyright for this software and a full listing 
 * of individual contributors are as shown in the packaged copyright.txt 
 * file. 
 * 
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer.
 *
 *  - Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution.
 *
 *  - Neither the name of the ORGANIZATION nor the names of its contributors may
 *    be used to endorse or promote products derived from this software without 
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE 
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 */
package org.ikasan.framework.web.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;
import org.ikasan.framework.configuration.ConfiguredResource;
import org.ikasan.framework.configuration.dao.ConfigurationDao;
import org.ikasan.framework.configuration.model.Configuration;
import org.ikasan.framework.configuration.model.ConfigurationParameter;
import org.ikasan.framework.flow.Flow;
import org.ikasan.framework.flow.FlowComponent;
import org.ikasan.framework.flow.FlowElement;
import org.ikasan.framework.flow.VisitingInvokerFlow;
import org.ikasan.framework.module.Module;
import org.ikasan.framework.module.service.ModuleService;
import org.ikasan.framework.systemevent.service.SystemEventService;
import org.springframework.binding.message.MessageBuilder;
import org.springframework.security.Authentication;
import org.springframework.security.context.SecurityContextHolder;
import org.springframework.webflow.execution.RequestContext;

/**
 * Configuration Management Service for maintenance of runtime component configurations.
 * 
 * @author Ikasan Development Team
 *
 */
public class ConfigurationManagementService
{
    /** constant for logging new configuration */
    public static final String CONFIGURATION_INSERT_SYSTEM_EVENT_ACTION = "Configuration created";

    /** constant for logging updated configuration */
    public static final String CONFIGURATION_UPDATE_SYSTEM_EVENT_ACTION = "Configuration updated";

    /** constant for logging deleted configuration */
    public static final String CONFIGURATION_DELETE_SYSTEM_EVENT_ACTION = "Configuration deleted";

    /** configuration DAO used for accessing the configuration */
    private ConfigurationDao configurationDao;
    
    /** system event service records all changes to configurations */
    private SystemEventService systemEventService;
    
    /** module service */
    private ModuleService moduleService;
    
    /**
     * Constructor
     * @param configurationDao
     * @param systemEventService
     * @param moduleService
     */
    public ConfigurationManagementService(ConfigurationDao configurationDao, SystemEventService systemEventService, 
            ModuleService moduleService)
    {
        this.configurationDao = configurationDao;
        if(configurationDao == null)
        {
            throw new IllegalArgumentException("configurationDao cannot be 'null'");
        }

        this.systemEventService = systemEventService;
        if(systemEventService == null)
        {
            throw new IllegalArgumentException("systemEventService cannot be 'null'");
        }

        this.moduleService = moduleService;
        if(moduleService == null)
        {
            throw new IllegalArgumentException("moduleService cannot be 'null'");
        }
    }
   
    /**
     * Is this moduleName/flowName/flowElementName referencing a component that
     * is marked as a ConfiguredResource.
     * @param moduleName
     * @param flowName
     * @param flowElementName
     * @return boolean
     */
    public boolean isConfiguredResource(String moduleName, String flowName, String flowElementName)
    {
        return ( getFlowComponent(moduleName, flowName, flowElementName) instanceof ConfiguredResource );
    }
    
    /**
     * Find the configuration instance for this moduleName/flowName/flowElementName.
     * Report any issues back via the RequestContext.
     * @param moduleName
     * @param flowName
     * @param flowElementName
     * @param context
     * @return Configuration
     */
    public Configuration findConfiguration(String moduleName, String flowName, String flowElementName, RequestContext context)
    {
        try
        {
            ConfiguredResource configuredResource = getConfiguredResource(moduleName, flowName, flowElementName);
            return this.configurationDao.findById(configuredResource.getConfiguredResourceId());
        }
        catch(RuntimeException e)
        {
            context.getMessageContext().addMessage(new MessageBuilder().error().source("findConfiguration").defaultText(
                    e.getMessage()).build());
        }
        
        return null;
    }

    /**
     * Create a new configuration instance for this moduleName/flowName/flowElementName.
     * Report any issues back via the RequestContext.
     * @param moduleName
     * @param flowName
     * @param flowElementName
     * @param context
     * @return Configuration
     */
    public Configuration createConfiguration(String moduleName, String flowName, String flowElementName, RequestContext context)
    {
        try
        {
            ConfiguredResource configuredResource = getConfiguredResource(moduleName, flowName, flowElementName);
            Object configuredObject = configuredResource.getConfiguration();
            if(configuredObject == null)
            {
                throw new RuntimeException("ConfiguredResource returned a 'null' configuration instance. "
                        + "Please ensure the ConfiguredResource returns a valid configuration instance. "
                        + "See ModuleName [" + moduleName 
                        + "] flowName [" + flowName + "] flowElementName ["
                        + flowElementName + "] resourceId [" + configuredResource.getConfiguredResourceId() + "]");
            }
            
            //PropertyDescriptor[] propertyDescriptors = PropertyUtils.getPropertyDescriptors(configuredObject);
            Configuration configuration = new Configuration(configuredResource.getConfiguredResourceId());
            List<ConfigurationParameter> configurationParameters = new ArrayList<ConfigurationParameter>();
            configuration.setConfigurationParameters(configurationParameters);

            try
            {
                Map<String,String> properties = BeanUtils.describe(configuredObject);
                for(Iterator it = properties.entrySet().iterator(); it.hasNext();) 
                {
                    Map.Entry<String,String> entry = (Map.Entry) it.next();
                    String name = entry.getKey();
                    String value = entry.getValue();

                    // TODO - is there a cleaner way of ignoring the class property ?
                    if(!"class".equals(name))
                    {
                        configurationParameters.add(new ConfigurationParameter(name, value));
                    }
                 }
            }
            catch(Exception e)
            {
                throw new RuntimeException(e);
            }

            return configuration;
        }
        catch(RuntimeException e)
        {
            context.getMessageContext().addMessage(new MessageBuilder().error().source("createConfiguration").defaultText(
                    e.getMessage()).build());
        }
        
        return null;
    }

    /**
     * Insert a new Configuration instance.
     * @param configuration
     */
    public void insertConfiguration(Configuration configuration)
    {
        this.systemEventService.logSystemEvent(configuration.getConfigurationId(), CONFIGURATION_INSERT_SYSTEM_EVENT_ACTION, getAuthentication().getName());
        this.configurationDao.save(configuration);
    }
    
    /**
     * Update an existing Configuration instance.
     * @param configuration
     */
    public void updateConfiguration(Configuration configuration)
    {
        this.systemEventService.logSystemEvent(configuration.getConfigurationId(), CONFIGURATION_UPDATE_SYSTEM_EVENT_ACTION, getAuthentication().getName());
        this.configurationDao.save(configuration);
    }
    
    /**
     * Delete an existing Configuration instance.
     * @param configuration
     */
    public void deleteConfiguration(Configuration configuration)
    {
        this.systemEventService.logSystemEvent(configuration.getConfigurationId(), CONFIGURATION_DELETE_SYSTEM_EVENT_ACTION, getAuthentication().getName());
        this.configurationDao.delete(configuration);
    }

    /**
     * Utility method for getting the authentication principal of the invoking user.
     * @return Authentication
     */
    protected Authentication getAuthentication()
    {
        return SecurityContextHolder.getContext().getAuthentication();
    }
    
    /**
     * Utility method for locating and returning the ConfiguredResource instance based on the given
     * moduleName/flowName/flowElementName.
     * @param moduleName
     * @param flowName
     * @param flowElementName
     * @return ConfiguredResource
     */
    private ConfiguredResource getConfiguredResource(String moduleName, String flowName, String flowElementName)
    {
        FlowComponent flowComponent = getFlowComponent(moduleName, flowName, flowElementName);
        if(flowComponent instanceof ConfiguredResource)
        {
            return (ConfiguredResource)flowComponent;
        }
        else
        {
            throw new UnsupportedOperationException("Component must be of type 'ConfiguredResource' to support component configuration");
        }
    }
    
    /**
     * Utility method for locating and returning the FlowComponent for the given
     * moduleName/flowName/flowElementName.
     * @param moduleName
     * @param flowName
     * @param flowElementName
     * @return FlowComponent
     */
    private FlowComponent getFlowComponent(String moduleName, String flowName, String flowElementName)
    {
        Module module = moduleService.getModule(moduleName);
        Flow flow = module.getFlows().get(flowName);
        if(flow instanceof VisitingInvokerFlow)
        {
            List<FlowElement> flowElements = ((VisitingInvokerFlow) flow).getFlowElements();
            for (FlowElement flowElement : flowElements)
            {
                if(flowElementName.equals(flowElement.getComponentName()))
                {
                    return flowElement.getFlowComponent();
                }
            }

            throw new RuntimeException("FlowComponent not found for module [" 
                + moduleName + "] flow [" + flowName + "] flowElementName [" + flowElementName + "]");
        }
        else
        {
            throw new UnsupportedOperationException("Flow must be an implementation of VisitingFlowInvoker to support configuration");
        }
    }
}
