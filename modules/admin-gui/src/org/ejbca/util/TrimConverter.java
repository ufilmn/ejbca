/*************************************************************************
 *                                                                       *
 *  EJBCA Community: The OpenSource Certificate Authority                *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.ejbca.util;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.ConverterException;
import javax.faces.convert.FacesConverter;

import org.apache.commons.lang.StringUtils;

/**
 * JSF converter that trims strings  
 * 
 * @version $Id: TrimConverter.java 31307 2019-01-24 13:02:39Z samuellb $
 */
@FacesConverter("trimConverter")
public class TrimConverter implements Converter {

    @Override
    public Object getAsObject(final FacesContext context, final UIComponent component, final String value) {
        return StringUtils.trim(value);
    }

    @Override
    public String getAsString(final FacesContext context, final UIComponent component, final Object value) throws ConverterException {
        if (value == null) {
            return "";
        }
        return (String) value;
    }
}

