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
package org.ikasan.dashboard.ui.mappingconfiguration.data;

import org.apache.log4j.Logger;
import org.ikasan.dashboard.ui.framework.group.RefreshGroup;
import org.ikasan.dashboard.ui.framework.util.DashboardSessionValueConstants;
import org.ikasan.dashboard.ui.mappingconfiguration.util.MappingConfigurationConstants;
import org.ikasan.mapping.model.ConfigurationType;
import org.ikasan.mapping.service.MappingConfigurationService;
import org.ikasan.security.service.authentication.IkasanAuthentication;
import org.ikasan.systemevent.service.SystemEventService;

import com.vaadin.data.Item;
import com.vaadin.data.fieldgroup.FieldGroup;
import com.vaadin.server.VaadinService;
import com.vaadin.ui.Field;

/**
 * @author Ikasan Development Team
 *
 */
public class NewConfigurationTypeFieldGroup extends FieldGroup
{
    /** Logger instance */
    private static Logger logger = Logger.getLogger(NewConfigurationTypeFieldGroup.class);

    private static final long serialVersionUID = -6584144145939855353L;

    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";

    private RefreshGroup refreshGroup;
    private MappingConfigurationService mappingConfigurationService;
    private SystemEventService systemEventService;

    /**
     * Constructor
     * 
     * @param refreshGroup
     * @param mappingConfigurationService
     */
    public NewConfigurationTypeFieldGroup(RefreshGroup refreshGroup
            , MappingConfigurationService mappingConfigurationService
            , SystemEventService systemEventService)
    {
        super();
        this.refreshGroup = refreshGroup;
        this.systemEventService = systemEventService;

        this.mappingConfigurationService = mappingConfigurationService;
    }

    /**
     * Constructor
     * 
     * @param itemDataSource
     * @param refreshGroup
     * @param mappingConfigurationService
     */
    public NewConfigurationTypeFieldGroup(Item itemDataSource, RefreshGroup refreshGroup
            , MappingConfigurationService mappingConfigurationService, SystemEventService systemEventService)
    {
        super(itemDataSource);
        this.refreshGroup = refreshGroup;
        this.systemEventService = systemEventService;
        this.mappingConfigurationService = mappingConfigurationService;
    }

    /* (non-Javadoc)
     * @see com.vaadin.data.fieldgroup.FieldGroup#commit()
     */
    @Override
    public void commit() throws CommitException
    {
        Field<String> name = (Field<String>) this.getField(NAME);

        ConfigurationType type = new ConfigurationType();
        type.setName(name.getValue());

        try
        {
            this.mappingConfigurationService.saveConfigurationType(type);

            IkasanAuthentication authentication = (IkasanAuthentication)VaadinService.getCurrentRequest().getWrappedSession()
                	.getAttribute(DashboardSessionValueConstants.USER);

            systemEventService.logSystemEvent(MappingConfigurationConstants.MAPPING_CONFIGURATION_SERVICE, 
            		"Created new mapping configuration type: " + type.getName(), authentication.getName());

            logger.debug("User: " + authentication.getName() 
                + " added a new Mapping Configuration Type:  " 
                    + type);
        }
        catch (Exception e)
        {
            throw new CommitException(e);
        }

        this.refreshGroup.refresh();
    }
}
