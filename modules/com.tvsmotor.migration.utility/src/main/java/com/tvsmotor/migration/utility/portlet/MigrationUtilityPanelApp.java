package com.tvsmotor.migration.utility.portlet;

import com.liferay.application.list.BasePanelApp;
import com.liferay.application.list.PanelApp;
import com.liferay.application.list.constants.PanelCategoryKeys;
import com.liferay.portal.kernel.model.Portlet;
import com.liferay.portal.kernel.service.PortletLocalServiceUtil;
import com.tvsmotor.migration.utility.constants.MigrationUtilityKeys;

import java.util.Locale;

import org.osgi.service.component.annotations.Component;

@Component(
    immediate = true,
    property = {
        "panel.app.order:Integer=100",                          // position
        "panel.category.key=" + PanelCategoryKeys.CONTROL_PANEL_CONFIGURATION
    },
    service = PanelApp.class
)
public class MigrationUtilityPanelApp extends BasePanelApp {

	@Override
	public String getPortletId() {
		return MigrationUtilityKeys.MIGRATIONUTILITY;
	}

	@Override
	public String getLabel(Locale locale) {
		return "Migration Utility";
	}
	
	@Override
	public Portlet getPortlet() {
		Portlet portlet = PortletLocalServiceUtil.getPortletById(getPortletId());
		return portlet;
	}
}
