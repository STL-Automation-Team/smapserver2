package org.smap.sdal.managers;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import org.smap.sdal.Utilities.ApplicationException;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.QueryGenerator;
import org.smap.sdal.model.FileDescription;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.OptionDesc;
import org.smap.sdal.model.QueryForm;
import org.smap.sdal.model.SqlDesc;
import org.smap.sdal.model.Survey;

/*****************************************************************************

This file is part of SMAP.

SMAP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SMAP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

 ******************************************************************************/

public class PDFReportsManager {

	private static Logger log =
			Logger.getLogger(PDFReportsManager.class.getName());

	LogManager lm = new LogManager();		// Application log

	boolean forDevice = false;	// URL prefixes should be in the client format
	
	// Global values set in constructor
	private ResourceBundle localisation;
	
	// Other global values
	int languageIdx = 0;

	boolean mExcludeEmpty = false;
	

	
	public PDFReportsManager(ResourceBundle l) {
		localisation = l;
	}
	
	/*
	 * Create the PDF Report
	 */
	public Response getReport(
			Connection sd,
			Connection cResults,
			String username,
			boolean temporaryUser,
			HttpServletRequest request,
			HttpServletResponse response,
			int sId, 
			String sIdent,
			String filename, 
			boolean landscape, 
			String language,
			Date startDate,
			Date endDate,
			int dateId,
			String filter) throws Exception {
		
		Response responseVal = null;
		String basePath = GeneralUtilityMethods.getBasePath(request);
		SurveyManager sm = new SurveyManager(localisation, "UTC");
		PreparedStatement pstmt = null;
		ZipOutputStream zos = null;
		
		String tz = "UTC";		// Default to UTC
		File folder = null;
		ArrayList<FileDescription> files = new ArrayList<> ();
		sId = GeneralUtilityMethods.getLatestSurveyId(sd, sId);
		try {
					
			/*
			 * Get the sql
			 */
			Form f = GeneralUtilityMethods.getTopLevelForm(sd, sId);
			if(!GeneralUtilityMethods.tableExists(cResults, f.tableName)) {
				log.info("Table: " + f.tableName + " does not exist. Presumably no data has been submitted to this survey");
				throw new ApplicationException(localisation.getString("msg_no_data"));
			}
			QueryManager qm = new QueryManager();	
			ArrayList<QueryForm> queryList = null;
			queryList = qm.getFormList(sd, sId, f.id);		// Get a form list for this survey / form combo

			QueryForm startingForm = qm.getQueryTree(sd, queryList);	// Convert the query list into a tree
			String urlprefix = GeneralUtilityMethods.getUrlPrefix(request);
			String attachmentPrefix = GeneralUtilityMethods.getAttachmentPrefix(request, forDevice);
			// hyperlink prefix assumes that the hyperlink will be used by a human, hence always use client authentication
			String hyperlinkPrefix = GeneralUtilityMethods.getAttachmentPrefix(request, false);
			HashMap<ArrayList<OptionDesc>, String> labelListMap = new  HashMap<ArrayList<OptionDesc>, String> ();
			SqlDesc sqlDesc = QueryGenerator.gen(
					sd, 
					cResults,
					localisation,
					sId,
					sIdent,
					f.id,
					language, 
					"pdf", 
					urlprefix,
					attachmentPrefix,
					true,
					true,
					false,
					labelListMap,
					false,
					false,			// suid
					request.getServerName().toLowerCase(),
					null,
					null,
					username,
					startDate,
					endDate,
					dateId,
					false,			// superUser - Always apply filters
					startingForm,
					filter,
					true,
					true,
					tz,
					null,
					true,		// Outer join of tables
					false		// Accuracy and Altitude
					);
			
			pstmt = cResults.prepareStatement(sqlDesc.sql);
			log.info("Get records to convert to PDF's: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			
			String filePath = basePath + "/temp/" + String.valueOf(UUID.randomUUID());
			folder = new File(filePath);
			folder.mkdir();
		
			while(rs.next()) {
				String instanceId = rs.getString("instanceid");
				
				// Get a name for the pdf file
				String name = null;
				try {
					name = rs.getString("instancename");		// Try the instance name
				} catch(Exception e) {}
				if(name == null || name.trim().length() == 0) {
					try {
						name = rs.getString("_hrk");					// Then try the HRK
					} catch(Exception e) {}
				}
				if(name == null || name.trim().length() == 0) {
					name = "r";									// Then, if there is still no name, Use the primary key
				}
				String escapedName = GeneralUtilityMethods.urlEncode(name);
				name = escapedName + rs.getString("prikey") + ".pdf";					// Add the primary key to guarantee uniqueness

				// Write the pdf to a temporary file
	 			Survey survey = sm.getById(sd, cResults, username, temporaryUser, sId, true, basePath, 
						instanceId, true, false, true, false, true, "real", 
						false, false, true, "geojson",
						false,			// Don't get child surveys
						false,			// launched only
						false		// Don't merge set value into default values
						);				
				PDFSurveyManager pm = new PDFSurveyManager(localisation, sd, cResults, survey, username, tz);
				
				String tempFilePath = filePath + "/" + name;
				File tempFile = new File(tempFilePath);
				FileOutputStream tempFileStream = new FileOutputStream(tempFile);
				int pdfTemplateId = GeneralUtilityMethods.testForPdfTemplate(sd, cResults, localisation, survey, username,
						instanceId, tz);
				
				pm.createPdf(tempFileStream, 
						basePath, 
						attachmentPrefix, 
						hyperlinkPrefix,
						username, 
						language, 
						pdfTemplateId,
						false, 
						filename, 
						landscape, 
						response);
				tempFileStream.close();
				files.add(new FileDescription(name, tempFilePath));
			}
			
			GeneralUtilityMethods.setFilenameInResponse(filename + ".zip", response);
			response.setHeader("Content-type",  "application/octet-stream; charset=UTF-8");
			
			zos = new ZipOutputStream(response.getOutputStream());
			GeneralUtilityMethods.writeFilesToZipOutputStream(zos, files);		// zos clased in call
			
		} finally {
			if(pstmt != null) {try {pstmt.close();} catch(Exception e) {}}
			
			// Clean up files
			for (FileDescription fd : files) {
				File f = new File(fd.path);
				try{f.delete();} catch(Exception e) {}
			}
			if(folder != null) {try {folder.delete();} catch(Exception e) {}}
		}
		
		return responseVal;
	}


}


