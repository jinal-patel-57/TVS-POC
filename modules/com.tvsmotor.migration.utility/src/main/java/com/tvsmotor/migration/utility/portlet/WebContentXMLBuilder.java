package com.tvsmotor.migration.utility.portlet;

import com.liferay.document.library.kernel.model.DLVersionNumberIncrease;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.document.library.kernel.service.DLAppLocalServiceUtil;
import com.liferay.document.library.kernel.util.DLUtil;
import com.liferay.journal.service.JournalArticleLocalServiceUtil;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.Folder;
import com.liferay.portal.kernel.service.GroupLocalServiceUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextFactory;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.MimeTypesUtil;
import com.tvsmotor.configuration.action.TvsMotorConfigurationAction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.UUID;

import javax.portlet.ActionRequest;

public class WebContentXMLBuilder {
	
	private static Log log = LogFactoryUtil.getLog(WebContentXMLBuilder.class);

	public static String buildFAQXml(String mainTitle, JSONArray items) {
		StringBuilder xml = new StringBuilder();

		xml.append("<?xml version=\"1.0\"?>");
		xml.append("<root available-locales=\"en_US\" default-locale=\"en_US\" version=\"1.0\">");

		// Main Title
		xml.append("<dynamic-element name=\"Text71181147\" type=\"text\" field-reference=\"mainTitle\" ")
				.append("index-type=\"keyword\" instance-id=\"").append(UUID.randomUUID().toString()).append("\">")
				.append("<dynamic-content language-id=\"en_US\"><![CDATA[").append(mainTitle)
				.append("]]></dynamic-content>").append("</dynamic-element>");

		// FAQs (repeatable fieldset)
		for (int i = 0; i < items.length(); i++) {
			JSONObject item = items.getJSONObject(i);
			String fieldsetId = UUID.randomUUID().toString();

			xml.append(
					"<dynamic-element name=\"Fieldset40591109\" type=\"fieldset\" field-reference=\"titleDescription\" repeatable=\"true\" ")
					.append("instance-id=\"").append(fieldsetId).append("\">");

			// Title inside fieldset
			xml.append("<dynamic-element name=\"Text01101924\" type=\"text\" field-reference=\"link\" ")
					.append("index-type=\"keyword\" instance-id=\"").append(UUID.randomUUID().toString()).append("\">")
					.append("<dynamic-content language-id=\"en_US\"><![CDATA[").append(item.get("link"))
					.append("]]></dynamic-content>").append("</dynamic-element>");

			xml.append("</dynamic-element>"); // close fieldset
		}

		xml.append("</root>");
		return xml.toString();
	}

	/**
	 * New builder: builds structured XML for your shared structure. - Reads
	 * pageTitle and metaDescription from sitecoreJson.fields - Builds repeatable
	 * Fieldset70304617 entries from Hero items
	 * 
	 * @param groupId
	 * @param userId
	 *
	 * @param sitecoreJson      the Sitecore-like JSON you provided (Liferay
	 *                          JSONObject)
	 * @param dlAppLocalService
	 * @param pageType
	 * @return XML string that can be used as JournalArticle content for structured
	 *         web content
	 * @throws Exception
	 */
	public static String buildFromInput(long userId, long groupId, JSONObject sitecoreComponent,
			DLAppLocalService dlAppLocalService, String pageType, ThemeDisplay themeDisplay,
			ActionRequest actionRequest) throws Exception {
		StringBuilder xml = new StringBuilder();

		JSONObject configJson = JSONFactoryUtil.createJSONObject(TvsMotorConfigurationAction.getMigrationMapping());

		xml.append("<?xml version=\"1.0\"?>");
		xml.append("<root available-locales=\"en_US\" default-locale=\"en_US\" version=\"1.0\">");

		String componentName = sitecoreComponent.getString("componentName");

		// Title field — update the name if your structure uses a different field name

		if("HeroBanner".equalsIgnoreCase(componentName)) {
			JSONObject sitecoreFields = sitecoreComponent.getJSONObject("fields");

			JSONArray sitecoreItems = sitecoreFields.getJSONArray("Banners");
			
			JSONArray configItems = configJson.getJSONObject("pages").getJSONObject(pageType).getJSONObject(componentName)
					.getJSONArray("items");
			
			JSONObject configComponentJson = configJson.getJSONObject("pages").getJSONObject(pageType).getJSONObject(componentName);
			
			if(configComponentJson.has("mainFieldsMapping")) {
				JSONObject configComponentMainFields = configComponentJson.getJSONObject("mainFieldsMapping");
				log.info("configComponentMainFields -- " + configComponentMainFields);
				for(String key: configComponentMainFields.keySet()) {
					log.info("configComponentMainFields keys -- " + key);
					xml.append("<dynamic-element name=\""+configComponentMainFields.getString(key)+"\" type=\"text\" field-reference=\""+key+"\" ")
					.append("index-type=\"\" instance-id=\"").append(UUID.randomUUID().toString()).append("\">")
					.append("<dynamic-content language-id=\"en_US\"><![CDATA[").append(escapeCdata(sitecoreFields.getString(key)))
					.append("]]></dynamic-content>").append("</dynamic-element>");
				}
				log.info("xml after main fields -- " + xml);
			}

			for (int j = 0; j < sitecoreItems.length(); j++) {
				JSONObject sitecoreItem = sitecoreItems.getJSONObject(j).getJSONObject("fields"); // image & link data

				// create fieldset instance id to group child fields
				String fieldsetId = UUID.randomUUID().toString();

				xml.append("<dynamic-element name=\""+configJson.getJSONObject("pages").getJSONObject(pageType).getJSONObject(componentName)
						.getString("itemsFieldName")+"\" type=\"fieldset\" field-reference=\""+configJson.getJSONObject("pages").getJSONObject(pageType).getJSONObject(componentName)
						.getString("itemsFieldName")+"\" ")
						.append("repeatable=\"true\" instance-id=\"").append(fieldsetId).append("\">");

				for (int k = 0; k < configItems.length(); k++) {
					JSONObject configItem = configItems.getJSONObject(k);
					String fieldType = configItem.getString("fieldType");
					String fieldStructureReference = configItem.getString("fieldStructureReference");
					String fieldStructureName = configItem.getString("fieldStructureName");

					String sitecoreFieldName = configItem.getString("sitecoreFieldName");
					
						switch (fieldType) {
						case "image":
							if(sitecoreItem.has(sitecoreFieldName)) {
								String url = sitecoreItem.getJSONObject(sitecoreFieldName).getJSONObject("value").has("src")? "https://www.tvsmotor.com" + sitecoreItem.getJSONObject(sitecoreFieldName).getJSONObject("value").getString("src"):"";
								ServiceContext serviceContext = ServiceContextFactory.getInstance(actionRequest);
								Folder pageTypeFolder = createFolder(pageType, 0l, themeDisplay, serviceContext, groupId);
								Folder componentFolder = createFolder(componentName, pageTypeFolder.getFolderId(), themeDisplay,
										serviceContext, groupId);
								log.info(
										"componentFolder -- " + componentFolder.getFolderId() + " -- " + componentFolder.getName());
								
								FileEntry fileEntry = WebContentXMLBuilder.uploadFromUrl(userId, groupId,
										componentFolder.getFolderId(), url, dlAppLocalService);
								String previewURL = DLUtil.getPreviewURL(fileEntry, fileEntry.getFileVersion(), themeDisplay, "");
								Group group = GroupLocalServiceUtil.getGroup(groupId);
								int dotIndex = fileEntry.getFileName().lastIndexOf('.');
								String title = (dotIndex > 0) ? fileEntry.getFileName().substring(0, dotIndex) : fileEntry.getFileName();
								previewURL=
									    themeDisplay.getPortalURL() +
									    themeDisplay.getPathContext() +
									    "/documents/d" +
									    group.getFriendlyURL() + "/" +
									    title;// +
									    //"?download=true";
								// Build a small JSON for image metadata (Liferay often stores image field as
								// JSON)
								
								JSONObject imageMeta = JSONFactoryUtil.createJSONObject();
								imageMeta.put("url", previewURL);
								imageMeta.put("title", fileEntry.getTitle());
								imageMeta.put("type", "document");
								imageMeta.put("fileEntryId", fileEntry.getFileEntryId());
								imageMeta.put("uuid", fileEntry.getUuid());
								imageMeta.put("description", "");
								imageMeta.put("groupId", groupId);
								imageMeta.put("alt", "");
								imageMeta.put("name", fileEntry.getFileName());
								//imageMeta.put("resourcePrimKey", fileEntry.getPrimaryKey());
								try {
									dlAppLocalService.getFileEntry(fileEntry.getFileEntryId());
									log.error("fileEntry.getFileEntryId() -- " + fileEntry.getFileEntryId() + " exists");
								} catch (Exception e) {
									log.error("fileEntry.getFileEntryId() -- " + fileEntry.getFileEntryId() + " doesnot exists");
								}
								
								xml.append("<dynamic-element name=\"" + fieldStructureName + "\" type=\"image\" field-reference=\""
										+ fieldStructureName + "\" ").append("index-type=\"text\" instance-id=\"")
								.append(UUID.randomUUID().toString()).append("\">")
								.append("<dynamic-content language-id=\"en_US\"><![CDATA[")
								.append(escapeCdata(imageMeta.toString())).append("]]></dynamic-content>")
								.append("</dynamic-element>");
							} else {

								JSONObject imageMeta = JSONFactoryUtil.createJSONObject();
								imageMeta.put("url", "");
								imageMeta.put("title", "");
								imageMeta.put("description", "");

								xml.append("<dynamic-element name=\"" + fieldStructureName
										+ "\" type=\"image\" field-reference=\"" + fieldStructureName + "\" ")
										.append("index-type=\"text\" instance-id=\"").append(UUID.randomUUID().toString())
										.append("\">").append("<dynamic-content language-id=\"en_US\"><![CDATA[")
										.append(escapeCdata(imageMeta.toString())).append("]]></dynamic-content>")
										.append("</dynamic-element>");
								 
								/*
								 * xml.append("<dynamic-element name=\"" + fieldStructureName +
								 * "\" type=\"image\" field-reference=\"" + fieldStructureName +
								 * "\" ").append("index-type=\"text\" instance-id=\"")
								 * .append(UUID.randomUUID().toString()).append("\"/>");
								 */
							}
							break;
						case "text":
							String link = sitecoreItem.getString(sitecoreFieldName);
							xml.append("<dynamic-element name=\"" + fieldStructureName + "\" type=\"text\" field-reference=\""
									+ fieldStructureName + "\" ").append("index-type=\"keyword\" instance-id=\"")
							.append(UUID.randomUUID().toString()).append("\">")
							.append("<dynamic-content language-id=\"en_US\"><![CDATA[").append(escapeCdata(link))
							.append("]]></dynamic-content>").append("</dynamic-element>");
							break;
						
						case "link":
							String linkUrl = sitecoreItem.getJSONObject(sitecoreFieldName).getJSONObject("value").getString("href");
							xml.append("<dynamic-element name=\"" + fieldStructureName + "\" type=\"text\" field-reference=\""
									+ fieldStructureName + "\" ").append("index-type=\"keyword\" instance-id=\"")
							.append(UUID.randomUUID().toString()).append("\">")
							.append("<dynamic-content language-id=\"en_US\"><![CDATA[").append(escapeCdata(linkUrl))
							.append("]]></dynamic-content>").append("</dynamic-element>");
							break;
						}
				}
				xml.append("</dynamic-element>"); // close fieldset
			}

			
		} else {
			log.info("in else not hero banner -- " + componentName);
		}
		xml.append("</root>");
		return xml.toString();
	}

	/**
	 * Utility to safely escape "]]>" sequences inside CDATA sections.
	 */
	private static String escapeCdata(String input) {
		if (input == null)
			return "";
		// split any closing CDATA sequence to avoid breaking XML
		return input.replace("]]>", "]]]]><![CDATA[>");
	}

	/**
	 * Downloads the file at fileUrl and uploads it to Documents & Media.
	 *
	 * @param userId         Liferay user ID performing the upload (must have
	 *                       permission).
	 * @param groupId        site (group) ID where the file should be stored
	 *                       (repositoryId).
	 * @param folderId       folderId inside D&M (0L for root).
	 * @param fileUrl        the external URL to fetch (e.g.
	 *                       "https://.../image.webp?...").
	 * @param serviceContext ServiceContext (should contain scopeGroupId and any
	 *                       permissions flags).
	 * @return created FileEntry
	 * @throws PortalException, IOException
	 */
	public static FileEntry uploadFromUrl(long userId, long groupId, long folderId, String fileUrl,
			DLAppLocalService dlAppLocalService) throws PortalException, IOException {

		ServiceContext serviceContext = new ServiceContext();
		serviceContext.setScopeGroupId(groupId);
		serviceContext.setUserId(userId);

		// Ensure serviceContext contains scope group and user
		if (serviceContext.getScopeGroupId() == 0) {
			serviceContext.setScopeGroupId(groupId);
		}

		// Optionally allow guests/groups (if you want)
		serviceContext.setAddGroupPermissions(true);
		serviceContext.setAddGuestPermissions(true);

		// Infer file name from URL path
		String fileName = extractFileNameFromUrl(fileUrl.replace(" ", "%20"));
		
		if (fileName == null || fileName.isEmpty()) {
			// fallback
			fileName = "uploaded-file";
		}

		// Download the file bytes
		byte[] bytes;
		try (InputStream in = new URL(fileUrl.replace(" ", "%20")).openStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

			byte[] buffer = new byte[8192];
			int n;
			while ((n = in.read(buffer)) != -1) {
				baos.write(buffer, 0, n);
			}
			bytes = baos.toByteArray();
		}

		String mimeType = MimeTypesUtil.getContentType(fileName); // Liferay helper
		if (mimeType == null || mimeType.isEmpty() || ContentTypes.APPLICATION_OCTET_STREAM.equals(mimeType)) {
			try (InputStream probeStream = new ByteArrayInputStream(bytes)) {
				String guessed = URLConnection.guessContentTypeFromStream(probeStream);
				if (guessed != null) {
					mimeType = guessed;
				}
			} catch (IOException e) {
				// ignore and fall back
			}
		}
		if (mimeType == null || mimeType.isEmpty()) {
			mimeType = ContentTypes.APPLICATION_OCTET_STREAM;
		}

		// repositoryId = groupId when storing in site Documents and Media
		String externalReferenceCode = UUID.randomUUID().toString();
		Date displayDate = new Date();
		int dotIndex = fileName.lastIndexOf('.');
		String title = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;
		File file = Files.write(Paths.get(fileName), bytes).toFile();
		
		try {
			FileEntry fileEntry = dlAppLocalService.getFileEntryByFileName(groupId, folderId, fileName);
			
			return dlAppLocalService.updateFileEntry(userId, fileEntry.getFileEntryId(), fileName, mimeType, fileName,
					null, null, "update", DLVersionNumberIncrease.MAJOR, bytes, null, null, null, serviceContext);
		} catch (PortalException pe) {
			//return dlAppLocalService.addFileEntry(externalReferenceCode, userId, groupId, folderId, fileName, mimeType,
				//	bytes, displayDate, null, null, serviceContext);
			
			return dlAppLocalService.addFileEntry(userId, groupId, folderId, fileName, mimeType, title, title, "", file, serviceContext);
		}
	}

	private static String extractFileNameFromUrl(String fileUrl) {
		try {
			URI uri = new URI(fileUrl);
			String path = uri.getPath();
			if (path == null) {
				return null;
			}
			return Paths.get(path).getFileName().toString();
		} catch (Exception e) {
			// fallback: attempt simple parsing
			int slash = fileUrl.lastIndexOf('/');
			if (slash >= 0 && slash < fileUrl.length() - 1) {
				String afterSlash = fileUrl.substring(slash + 1);
				int q = afterSlash.indexOf('?');
				if (q > 0) {
					return afterSlash.substring(0, q);
				} else {
					return afterSlash;
				}
			}
			return null;
		}
	}

	private static Folder createFolder(String folderName, long parentFolderId, ThemeDisplay themeDisplay,
			ServiceContext serviceContext, long groupId) throws Exception {
		try {
			return DLAppLocalServiceUtil.getFolder(groupId, parentFolderId, folderName);
		} catch (Exception e) {
			return DLAppLocalServiceUtil.addFolder(StringPool.BLANK, themeDisplay.getUserId(), groupId, parentFolderId,
					folderName, "Auto-created folder for " + folderName, serviceContext);
		}
	}
}
