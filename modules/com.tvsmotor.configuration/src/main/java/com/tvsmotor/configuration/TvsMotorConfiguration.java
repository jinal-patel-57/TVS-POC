package com.tvsmotor.configuration;

import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;

import aQute.bnd.annotation.metatype.Meta.AD;
import aQute.bnd.annotation.metatype.Meta.OCD;

@ExtendedObjectClassDefinition(category = "tvsmotor-configuration", scope = ExtendedObjectClassDefinition.Scope.SYSTEM)
@OCD(id="com.tvsmotor.configuration.TvsMotorConfiguration", localization = "content/Language", name = "tvsmotor-configuration")
public interface TvsMotorConfiguration {

	@AD(required = false, deflt = "", name = "Migration Mapping", description = "migration-mapping")
	public String getMigrationMapping();
	
}
