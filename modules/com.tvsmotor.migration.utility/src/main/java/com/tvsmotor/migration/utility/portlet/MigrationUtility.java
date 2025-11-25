package com.tvsmotor.migration.utility.portlet;

import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalServiceUtil;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONException;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextFactory;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.upload.UploadPortletRequest;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.tvsmotor.configuration.action.TvsMotorConfigurationAction;
import com.tvsmotor.migration.utility.constants.MigrationUtilityKeys;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.Portlet;
import javax.portlet.PortletException;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Migration Utility Portlet - corrected addArticle signature (articleId first)
 */
@Component(property = { "com.liferay.portlet.display-category=category.hidden",
		"com.liferay.portlet.header-portlet-css=/css/main.css", "com.liferay.portlet.instanceable=false",
		"javax.portlet.display-name=Tvs Motor Migration Utility", "javax.portlet.init-param.template-path=/",
		"javax.portlet.init-param.view-template=/view.jsp",
		"javax.portlet.name=" + MigrationUtilityKeys.MIGRATIONUTILITY, "javax.portlet.resource-bundle=content.Language",
		"javax.portlet.security-role-ref=administrator,power-user,user" }, service = Portlet.class)
public class MigrationUtility extends MVCPortlet {

	private Log log = LogFactoryUtil.getLog(MigrationUtility.class);
	
	@Reference
	protected JournalArticleLocalService journalArticleLocalService;
	
	@Reference
    private DLAppLocalService dlAppLocalService;

	@Override
	public void render(RenderRequest renderRequest, RenderResponse renderResponse)
			throws IOException, PortletException {

		try {
			if (Validator.isNotNull(TvsMotorConfigurationAction.getMigrationMapping())) {
				JSONObject mainMappingJson = JSONFactoryUtil
						.createJSONObject(TvsMotorConfigurationAction.getMigrationMapping());

				if (mainMappingJson.has("pages")) {
					renderRequest.setAttribute("pageList", mainMappingJson.getJSONObject("pages").keySet());
				}
			}
			super.render(renderRequest, renderResponse);
		} catch (JSONException e) {
			e.printStackTrace();
			throw new PortletException(e);
		}
	}

	@Override
	public void processAction(ActionRequest actionRequest, ActionResponse actionResponse) throws IOException, PortletException {

		ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);
		
		try {
			JSONObject configJson = JSONFactoryUtil.createJSONObject(TvsMotorConfigurationAction.getMigrationMapping());
			long groupId = configJson.getLong("siteId");
			String pageType = ParamUtil.getString(actionRequest, "pageType");

			UploadPortletRequest uploadRequest = PortalUtil.getUploadPortletRequest(actionRequest);
			File siteCoreJsonFile = uploadRequest.getFile("jsonFile");

			String jsonString = Files.readString(siteCoreJsonFile.toPath(), StandardCharsets.UTF_8);

			JSONObject sitecoreJson = JSONFactoryUtil.createJSONObject(jsonString);
			// Determine userId and groupId
			long userId = PortalUtil.getUserId(actionRequest);

			JSONArray sitecoreComponents = sitecoreJson.getJSONObject("sitecore").getJSONObject("route").getJSONObject("placeholders")
					.getJSONArray("jss-main");
            for (int i = 0; i < sitecoreComponents.length(); i++) {
                JSONObject sitecoreComponent = sitecoreComponents.getJSONObject(i);
                //String componentName = sitecoreComponent.getString("component", "");
                
                // Build XML content using WebContentXMLBuilder
    			String contentXml = WebContentXMLBuilder.buildFromInput(userId, groupId, sitecoreComponent, dlAppLocalService, pageType, themeDisplay, actionRequest);
    			log.info("Generated XML: " + contentXml);
    			// Resolve DDM structure by name (from config mapping)
    			String componentName = sitecoreComponent.getString("componentName");
    			if("TvsCare".equalsIgnoreCase(componentName)) {
    				Locale locale = LocaleUtil.fromLanguageId("en_US");
    				Map<Locale, String> titleMap = new HashMap<>();
    				titleMap.put(locale, componentName);
    				
    				Map<Locale, String> descriptionMap = new HashMap<>();
    				descriptionMap.put(locale, componentName);
    				
    				// ServiceContext
    				ServiceContext serviceContext = ServiceContextFactory.getInstance(JournalArticle.class.getName(),
    						actionRequest);
    				serviceContext.setScopeGroupId(groupId);
    				serviceContext.setUserId(userId);
    				serviceContext.setWorkflowAction(WorkflowConstants.ACTION_PUBLISH);
    				
    				
    				
    				String structureName = configJson.getJSONObject("pages").getJSONObject(pageType).getJSONObject(componentName).getString("structureName");
    				
    				if (structureName == null || structureName.isEmpty()) {
    					throw new IllegalArgumentException("structureName not found in mapping for pageType: " + pageType);
    				}
    				
    				// Find structure by name among all structures
    				DDMStructure ddmStructure = getStructureByName(groupId, structureName, LocaleUtil.getDefault());
    				
    				if (ddmStructure == null) {
    					throw new IllegalStateException(
    							"DDM structure with name '" + structureName + "' not found for group " + groupId);
    				}
    				
    				// Use numeric structureId (as you requested)
    				// long ddmStructureId = ddmStructure.getStructureId();
    				long ddmStructureId = Long.valueOf(ddmStructure.getStructureId());
    				String ddmTemplateKey = null; // set if you have a template
    				
    				// Generate a unique articleId string required by this addArticle overload
    				DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneId.of("UTC"));
    				String ts = fmt.format(Instant.now());
    				String articleERC = "tvsmotor-" + ts;
    				
    				long folderId = configJson.getJSONObject("pages").getJSONObject(pageType).getJSONObject(componentName).getLong("folderId");
    				
    				// Use the correct addArticle overload: articleId first, then userId, groupId,
    				// folderId, ...
    				JournalArticle article = journalArticleLocalService.addArticle(articleERC, userId, groupId, folderId,
    						titleMap, descriptionMap, contentXml, ddmStructureId, ddmTemplateKey, serviceContext);
    				
    				
    				log.info("Created JournalArticle: articleId=" + article.getArticleId() + ", resourcePrimKey="
    						+ article.getResourcePrimKey());
    			} else {
    				log.info("not hero banner -- " + componentName);
    			}
            }
		} catch (NoSuchMethodError nsme) {
			nsme.printStackTrace();
			throw new PortletException(
					"addArticle overload not available in this Liferay build. Please check available signatures.",
					nsme);
		} catch (Exception e) {
			e.printStackTrace();
			throw new PortletException(e);
		}
	}

	/**
	 * Find a DDMStructure by its localized name.
	 */
	public DDMStructure getStructureByName(long groupId, String structureName, Locale locale) {

		List<DDMStructure> structures = DDMStructureLocalServiceUtil.getDDMStructures(-1, -1);

		if (structures == null || structures.isEmpty()) {
			return null;
		}

		for (DDMStructure structure : structures) {
			try {
				String name = structure.getName(locale);
				if (name != null && name.equals(structureName)) {
					return structure;
				}
			} catch (Exception e) {
				// ignore locales not present on structure
			}
		}

		return null;
	}
}
