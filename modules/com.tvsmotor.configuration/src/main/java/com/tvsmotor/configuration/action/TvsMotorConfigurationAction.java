package com.tvsmotor.configuration.action;

import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.tvsmotor.configuration.TvsMotorConfiguration;

import java.util.Map;

import org.osgi.service.component.annotations.Component;

/**
 * @author Jinal Patel
 */
@Component(
	configurationPid = "com.tvsmotor.configuration.TvsMotorConfiguration",
	enabled = true,
	immediate = true
)
public class TvsMotorConfigurationAction {
	
	private volatile TvsMotorConfiguration tvsMotorConfiguration;
	
	private static String migrationMapping;

	public static String getMigrationMapping() {
		return migrationMapping;
	}
	
	public static void setMigrationMapping(String migrationMapping) {
		TvsMotorConfigurationAction.migrationMapping = migrationMapping;
	}
		
	protected void activate(Map<String,Object> properties) {
		tvsMotorConfiguration = ConfigurableUtil.createConfigurable(TvsMotorConfiguration.class, properties);
		migrationMapping = tvsMotorConfiguration.getMigrationMapping();
		
	} 
}
