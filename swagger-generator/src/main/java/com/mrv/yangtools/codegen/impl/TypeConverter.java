/*
 * Copyright (c) 2016 MRV Communications, Inc. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Christopher Murch <cmurch@mrv.com>
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen.impl;

import java.util.List;

import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.BitsTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.BooleanTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.EnumTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.IntegerTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LengthConstraint;
import org.opendaylight.yangtools.yang.model.api.type.PatternConstraint;
import org.opendaylight.yangtools.yang.model.api.type.StringTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.UnionTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.UnsignedIntegerTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.opendaylight.yangtools.yang.model.util.type.BaseTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mrv.yangtools.codegen.DataObjectBuilder;

import io.swagger.models.properties.BaseIntegerProperty;
import io.swagger.models.properties.BooleanProperty;
import io.swagger.models.properties.IntegerProperty;
import io.swagger.models.properties.LongProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;

/**
 * Supports type conversion between YANG and swagger
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class TypeConverter {

    private SchemaContext ctx;
    private DataObjectBuilder dataObjectBuilder;

    public TypeConverter(SchemaContext ctx) {
        this.ctx = ctx;
    }

    private static final Logger log = LoggerFactory.getLogger(TypeConverter.class);

    /**
     * Convert YANG type to swagger property
     * @param type YANG
     * @param parent for scope computation (to support leafrefs)
     * @return property
     */
    @SuppressWarnings("ConstantConditions")
    public Property convert(TypeDefinition<?> type, SchemaNode parent) {
        TypeDefinition<?> baseType = type.getBaseType();
        if(baseType == null) baseType = type;

        if(type instanceof LeafrefTypeDefinition) {
            log.debug("leaf node {}",  type);
            baseType = SchemaContextUtil.getBaseTypeForLeafRef((LeafrefTypeDefinition) type, ctx, parent);
        }

        if(baseType instanceof BooleanTypeDefinition) {
            return new BooleanProperty();
        }

        if(baseType instanceof IntegerTypeDefinition || baseType instanceof UnsignedIntegerTypeDefinition) {
            //TODO [bmi] how to map int8 type ???
            BaseIntegerProperty integer = new IntegerProperty();
            if (BaseTypes.isInt64(baseType) || BaseTypes.isUint32(baseType)) {
                integer = new LongProperty();
            }
            return integer;
        }
        if(baseType instanceof BitsTypeDefinition) {
            String refString = dataObjectBuilder.addModelForComplexTypes(type);
            return new RefProperty(refString);
        }
        
        if(baseType instanceof UnionTypeDefinition) {
            String refString = dataObjectBuilder.addModelForComplexTypes(type);
            return new RefProperty(refString);
        }

        EnumTypeDefinition e = toEnum(type);
        if(e != null) {
            if(enumToModel()) {
                String refString = dataObjectBuilder.addModel(e);
                return new RefProperty(refString);
            }
        }
        
        
		if (baseType instanceof StringTypeDefinition) {
			StringProperty stringProperty = new StringProperty();
			LengthConstraint lengthConstraint = getLengthConstraint(type);
			if (lengthConstraint != null) {
				stringProperty.setMinLength(Integer.valueOf(lengthConstraint.getMin().toString()));
				stringProperty.setMaxLength(Integer.valueOf(lengthConstraint.getMax().toString()));
				log.debug("convert: set string property range for parent={}, type={}, min value={}, max value={}",
						parent.getQName(),
						type.getQName(), stringProperty.getMinLength(), stringProperty.getMaxLength());
			}
 
			String pattern = getPattern(type);
			if (!pattern.isBlank()) {
				stringProperty.setPattern(pattern);
				log.debug("convert: set string property pattern for parent={}, type={}, pattern={}", parent.getQName(),
						type.getQName(), pattern);
			}
 
			log.debug("convert: string property for parent={}, type={}, property={}", parent.getQName(),
					type.getQName(), stringProperty);
			return stringProperty;
		}

        return new StringProperty();
    }
    
	private String getPattern(TypeDefinition<?> type) {

		String pattern = "";
		if (type instanceof StringTypeDefinition) {
			StringTypeDefinition sdef = (StringTypeDefinition) type;
			List<PatternConstraint> patterns = sdef.getPatternConstraints();
			if (!patterns.isEmpty()) {
				PatternConstraint patternConstraint = patterns.get(0);
				pattern = patternConstraint.getRegularExpression();
			}
		}
		if (pattern.isBlank() && type != null) {
			pattern = getPattern(type.getBaseType());
		}
		
		return pattern;
	}



	private LengthConstraint getLengthConstraint(TypeDefinition<?> type) {

		LengthConstraint lengthConstraint = null;
		if (type instanceof StringTypeDefinition) {
			StringTypeDefinition sdef = (StringTypeDefinition) type;
			lengthConstraint = sdef.getLengthConstraints() != null ? sdef.getLengthConstraints().get(0) : null;
		}

		if (lengthConstraint == null && type != null) {
			lengthConstraint = getLengthConstraint(type.getBaseType());
		}

		return lengthConstraint;

	}

    /**
     * Check if builder is present.
     * @return <code>true</code>
     * @throws IllegalStateException in case it is not present
     */
    protected boolean enumToModel() {
        if(dataObjectBuilder == null) throw new IllegalStateException("no data object builder configured");
        return true;
    }

    private EnumTypeDefinition toEnum(TypeDefinition<?> type) {
        if(type instanceof  EnumTypeDefinition) return (EnumTypeDefinition) type;
        if(type.getBaseType() instanceof  EnumTypeDefinition) return (EnumTypeDefinition) type.getBaseType();
        return null;
    }


    public void setDataObjectBuilder(DataObjectBuilder dataObjectBuilder) {
        this.dataObjectBuilder = dataObjectBuilder;
    }
}
