package koboToolboxApi;
/*
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

*/

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import managers.DataManager;
import model.DataEndPoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.Column;
import org.smap.sdal.model.Survey;

import utils.Utils;

/*
 * Provides access to collected data
 */
@Path("/v1/data")
public class Data extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(Data.class.getName());

	LogManager lm = new LogManager();		// Application log
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Data.class);
		return s;
	}
	
	public Data() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
	}
	
	/*
	 * KoboToolBox API version 1 /data
	 * Returns a list of data end points
	 */
	@GET
	@Produces("application/json")
	public Response getData(@Context HttpServletRequest request) { 
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("koboToolBoxAPI-getData");
		a.isAuthorised(sd, request.getRemoteUser());
		
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().build();
		}
		
		DataManager dm = new DataManager();
		try {
			ArrayList<DataEndPoint> data = dm.getDataEndPoints(sd, request, false);
			
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
			String resp = gson.toJson(data);
			response = Response.ok(resp).build();
		} catch (SQLException e) {
			e.printStackTrace();
			response = Response.serverError().build();
		} finally {
			SDDataSource.closeConnection("koboToolBoxAPI-getData", sd);
		}

		return response;
	}
	
	/*
	 * KoboToolBox API version 1 /data
	 * Get records for an individual survey in JSON format
	 */
	@GET
	@Produces("application/json")
	@Path("/{sId}")
	public Response getDataRecords(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@QueryParam("start") int start,
			@QueryParam("limit") int limit,
			@QueryParam("mgmt") boolean mgmt,
			@QueryParam("group") boolean group,			// If set include a dummy group value in the response, used by duplicate query
			@QueryParam("sort") String sort,			// Column Human Name to sort on
			@QueryParam("dirn") String dirn,			// Sort direction, asc || desc
			@QueryParam("form") int fId,				// Form id (optional only specify for a child form)
			@QueryParam("parkey") int parkey,			// Parent key (optional, use to get records that correspond to a single parent record)
			@QueryParam("hrk") String hrk,				// Unique key (optional, use to restrict records to a specific hrk)
			@QueryParam("format") String format			// dt for datatables otherwise assume kobo
			) { 
		
		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("koboToolboxApi - get data records");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		lm.writeLog(sd, sId, request.getRemoteUser(), "view", "Managed Forms or the API. " + (hrk == null ? "" : "Hrk: " + hrk));
		
		Connection cResults = ResultsDataSource.getConnection("koboToolboxApi - get data records");
		
		String sqlGetMainForm = "select f_id, table_name from form where s_id = ? and parentform = 0;";
		PreparedStatement pstmtGetMainForm = null;
		
		String sqlGetForm = "select parentform, table_name from form where s_id = ? and f_id = ?;";
		PreparedStatement pstmtGetForm = null;
		
		
		PreparedStatement pstmtGetData = null;
		
		StringBuffer columnSelect = new StringBuffer();
		String table_name = null;
		int parentform = 0;
		ResultSet rs = null;
		JSONArray ja = new JSONArray();

		if(sort != null && dirn == null) {
			dirn = "asc";
		}
		
		boolean isDt = false;
		if(format != null && format.equals("dt")) {
			isDt = true;
		}
		
		try {

			String urlprefix = GeneralUtilityMethods.getUrlPrefix(request);
			
			if(fId == 0) {
				pstmtGetMainForm = sd.prepareStatement(sqlGetMainForm);
				pstmtGetMainForm.setInt(1,sId);
				
				log.info("Getting main form: " + pstmtGetMainForm.toString() );
				rs = pstmtGetMainForm.executeQuery();
				if(rs.next()) {
					fId = rs.getInt(1);
					table_name = rs.getString(2);
				}
			} else {
				pstmtGetForm = sd.prepareStatement(sqlGetForm);
				pstmtGetForm.setInt(1,sId);
				pstmtGetForm.setInt(2,fId);
				
				log.info("Getting specific form: " + pstmtGetForm.toString() );
				rs = pstmtGetForm.executeQuery();
				if(rs.next()) {
					parentform = rs.getInt(1);
					table_name = rs.getString(2);
				}
			}
				
			ArrayList<Column> columns = GeneralUtilityMethods.getColumnsInForm(
					sd,
					cResults,
					parentform,
					fId,
					table_name,
					false,
					false,		// Don't include parent key
					false,		// Don't include "bad" columns
					true		// include instance id
					);
			
			if(mgmt) {
				GeneralUtilityMethods.addManagementColumns(columns);
			}
			
			for(int i = 0; i < columns.size(); i++) {
				Column c = columns.get(i);
				if(i > 0) {
					columnSelect.append(",");
				}
				columnSelect.append(c.getSqlSelect(urlprefix));
			}
			
			if(GeneralUtilityMethods.tableExists(cResults, table_name)) {
				
				String sqlGetData = "select " + columnSelect.toString() + " from " + table_name
						+ " where prikey >= ? "
						+ "and _bad = 'false'";
				String sqlSelect = "";
				if(parkey > 0) {
					sqlSelect = " and parkey = ?";
				}
				if(hrk != null) {
					sqlSelect = " and _hrk = ?";
				}
				
				String sqlGetDataOrder = null;
				if(sort != null) {
					// User has requested a specific sort order
					sqlGetDataOrder = " order by " + getSortColumn(columns, sort) + " " + dirn + ";";
				} else {
					// Set default sort order
					if(mgmt) {
						sqlGetDataOrder = " order by prikey desc limit 10000";
					} else {
						sqlGetDataOrder = " order by prikey asc;";
					}
				}
				
				pstmtGetData = cResults.prepareStatement(sqlGetData + sqlSelect + sqlGetDataOrder);
				int paramCount = 1;
				pstmtGetData.setInt(paramCount++, start);
				if(parkey > 0) {
					pstmtGetData.setInt(paramCount++, parkey);
				}
				if(hrk != null) {
					pstmtGetData.setString(paramCount++, hrk);
				}
				
				log.info("Get data: " + pstmtGetData.toString());
				rs = pstmtGetData.executeQuery();
				
				int index = 0;
				while (rs.next()) {
					
					if(limit > 0 && index >= limit) {
						break;
					}
					index++;
					
					JSONObject jr = new JSONObject();
					if(group) {
						jr.put("_group", "");				// _group for duplicate queries
					}
					for(int i = 0; i < columns.size(); i++) {	
						
						Column c = columns.get(i);
						String name = null;
						String value = null;
						
						if(c.isGeometry()) {							
							// Add Geometry (assume one geometry type per table)
							String geomValue = rs.getString(i + 1);	
							name = "_geolocation";
							JSONArray coords = null;
							if(geomValue != null) {
								JSONObject jg = new JSONObject(geomValue);									
								coords = jg.getJSONArray("coordinates");
							} else {
								coords = new JSONArray();
							}
							jr.put(name, coords);
				
						} else {
							
							//String name = rsMetaData.getColumnName(i);	
							name = c.humanName;
							value = rs.getString(i + 1);	
							
							if(value == null) {
								value = "";
							}
							
							if(name != null ) {
								if(!isDt) {
									name = Utils.translateToKobo(name);
								}
								jr.put(name, value);
							}
						}
						
				
					}
						
					ja.put(jr);
				}
						
			} 
			
			if(isDt) {
				JSONObject dt  = new JSONObject();
				dt.put("data", ja);
				response = Response.ok(dt.toString()).build();
			} else {
				response = Response.ok(ja.toString()).build();
			}
			
			
			
			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			try {if (pstmtGetMainForm != null) {pstmtGetMainForm.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetForm != null) {pstmtGetForm.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetData != null) {pstmtGetData.close();	}} catch (SQLException e) {	}
			
			ResultsDataSource.closeConnection("koboToolboxApi - get data records", cResults);			
			SDDataSource.closeConnection("koboToolboxApi - get data records", sd);
		}
		
		return response;
		
	}
	
	/*
	 * Get similar records for an individual survey in JSON format
	 */
	@GET
	@Produces("application/json")
	@Path("/similar/{sId}/{select}")
	public Response getSimilarDataRecords(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("select") String select,			// comma separated list of qname::function
														//  where function is none || lower
			@QueryParam("start") int start,
			@QueryParam("limit") int limit,
			@QueryParam("mgmt") boolean mgmt,
			@QueryParam("form") int fId,				// Form id (optional only specify for a child form)
			@QueryParam("format") String format			// dt for datatables otherwise assume kobo
			) { 
		
		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("koboToolboxApi - get data records");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		Connection cResults = ResultsDataSource.getConnection("koboToolboxApi - get similar data records");

		String sqlGetMainForm = "select f_id, table_name from form where s_id = ? and parentform = 0;";
		PreparedStatement pstmtGetMainForm = null;
		
		String sqlGetForm = "select parentform, table_name from form where s_id = ? and f_id = ?;";
		PreparedStatement pstmtGetForm = null;
		
		PreparedStatement pstmtGetSimilar = null;
		PreparedStatement pstmtGetData = null;
		
		StringBuffer columnSelect = new StringBuffer();
		StringBuffer similarWhere = new StringBuffer();
		ArrayList<String> groupTypes = new ArrayList<String> ();
		int groupColumns = 0;
		String table_name = null;
		int parentform = 0;
		ResultSet rs = null;
		JSONArray ja = new JSONArray();
		
		boolean isDt = false;
		if(format != null && format.equals("dt")) {
			isDt = true;
		}
		
		try {

			String urlprefix = GeneralUtilityMethods.getUrlPrefix(request);
			
			if(fId == 0) {
				pstmtGetMainForm = sd.prepareStatement(sqlGetMainForm);
				pstmtGetMainForm.setInt(1,sId);
				
				log.info("Getting main form: " + pstmtGetMainForm.toString() );
				rs = pstmtGetMainForm.executeQuery();
				if(rs.next()) {
					fId = rs.getInt(1);
					table_name = rs.getString(2);
				}
			} else {
				pstmtGetForm = sd.prepareStatement(sqlGetForm);
				pstmtGetForm.setInt(1,sId);
				pstmtGetForm.setInt(2,fId);
				
				log.info("Getting specific form: " + pstmtGetForm.toString() );
				rs = pstmtGetForm.executeQuery();
				if(rs.next()) {
					parentform = rs.getInt(1);
					table_name = rs.getString(2);
				}
			}
				
			ArrayList<Column> columns = GeneralUtilityMethods.getColumnsInForm(
					sd,
					cResults,
					parentform,
					fId,
					table_name,
					false,
					false,		// Don't include parent key
					false,		// Don't include "bad" columns
					true		// include instance id
					);
			
			if(mgmt) {
				GeneralUtilityMethods.addManagementColumns(columns);
			}
			
			if(GeneralUtilityMethods.tableExists(cResults, table_name)) {
				
				/*
				 * 1. Prepare the data query minus the where clause that is created to select similar rows
				 */
				for(int i = 0; i < columns.size(); i++) {
					Column c = columns.get(i);
					if(i > 0) {
						columnSelect.append(",");
					}
					columnSelect.append(c.getSqlSelect(urlprefix));
				}
				
					
				String sqlGetData = "select " + columnSelect.toString() + " from " + table_name
						+ " where prikey >= ? "
						+ "and _bad = 'false' ";
				String sqlSelect = "";
				
				String sqlGetDataOrder = null;
				
				// Set default sort order
				if(mgmt) {
					sqlGetDataOrder = " order by prikey desc limit 10000";
				} else {
					sqlGetDataOrder = " order by prikey asc;";
				}
				
				/*
				 * 1. Find the groups of similar records
				 */
				columnSelect = new StringBuffer();
				String [] selectPairs = select.split(",");
				for(int i = 0; i < selectPairs.length; i++) {
					String [] aSelect = selectPairs[i].split("::");
					if(aSelect.length > 1) {
						System.out.println("Pair: " + aSelect[0] + " : " + aSelect[1]);
						for(int j = 0; j < columns.size(); j++) {
							//System.out.println("name: " + columns.get(j).name);
							if(columns.get(j).name.equals(aSelect[0])) {
								Column c = columns.get(j);
								boolean stringFnApplies = false;
								
								if(c.qType.equals("string") || c.qType.equals("select1")
										|| c.qType.equals("barcode")) {
									stringFnApplies = true;
								}
								
								if( groupColumns > 0) {
									columnSelect.append(",");
								}
								similarWhere.append(" and ");
								
								if(stringFnApplies 
										&& (aSelect[1].equals("lower") 
										|| aSelect[1].equals("soundex"))) {
									String s = aSelect[1] +"(" + c.getSqlSelect(urlprefix) + ")";
									columnSelect.append(s);
									similarWhere.append(s + " = ?");
								} else {
									String s = c.getSqlSelect(urlprefix);
									columnSelect.append(s);
									similarWhere.append(s + " = ?");
								}
								groupColumns++;
								groupTypes.add(c.qType);
								break;
							}
						}
					}
				}
			
				if(columnSelect.length() == 0) {
					throw new Exception("No Matching Columns");
				}
				
				String sqlGetSimilar = "select count(*), " + columnSelect.toString()
						+ " from " + table_name
						+ " where prikey >= ? "
						+ "and _bad = 'false'";
				String sqlGroup = " group by " + columnSelect.toString();
				String sqlHaving = " having count(*) > 1 ";
			
				
				pstmtGetSimilar = cResults.prepareStatement(sqlGetSimilar + sqlGroup + sqlHaving);
				pstmtGetSimilar.setInt(1, start);
				System.out.println("Get Similar: " + pstmtGetSimilar.toString());
				rs = pstmtGetSimilar.executeQuery();
				
				/*
				 * For each grouping of similar records get the individual records
				 */
				while(rs.next()) {

					
					/*
					 * 3. Get the data that make up these similar records
					 */
					String groupKey = "";
					pstmtGetData = cResults.prepareStatement(sqlGetData + sqlSelect + similarWhere.toString() 
						+ sqlGetDataOrder);
					int paramCount = 1;
					pstmtGetData.setInt(paramCount++, start);
					for(int i = 0; i < groupColumns; i++) {
						String gType = groupTypes.get(i);
						log.info("Adding group type: " + gType);
						if(gType.equals("int")) {
							pstmtGetData.setInt(paramCount++, rs.getInt(i + 2));	
						} else { 
							pstmtGetData.setString(paramCount++, rs.getString(i + 2));
						}
						if(i > 0) {
							groupKey += "::";
						}
						groupKey += rs.getString(i + 2);
					}
					log.info("Get data: " + pstmtGetData.toString());
					ResultSet rsD = pstmtGetData.executeQuery();
					
					int index = 0;
					while (rsD.next()) {
						
						if(limit > 0 && index >= limit) {
							break;
						}
						index++;
						
						JSONObject jr = new JSONObject();
						jr.put("_group", groupKey);
						for(int i = 0; i < columns.size(); i++) {	
							
							Column c = columns.get(i);
							String name = null;
							String value = null;
							
							if(c.isGeometry()) {							
								// Add Geometry (assume one geometry type per table)
								String geomValue = rsD.getString(i + 1);	
								name = "_geolocation";
								JSONArray coords = null;
								if(geomValue != null) {
									JSONObject jg = new JSONObject(geomValue);									
									coords = jg.getJSONArray("coordinates");
								} else {
									coords = new JSONArray();
								}
								jr.put(name, coords);
					
							} else {
								
								//String name = rsMetaData.getColumnName(i);	
								name = c.humanName;
								value = rsD.getString(i + 1);	
								
								if(value == null) {
									value = "";
								}
								
								if(name != null ) {
									if(!isDt) {
										name = Utils.translateToKobo(name);
									}
									jr.put(name, value);
								}
							}
							
					
						}
							
						ja.put(jr);
					}
					rsD.close();
				}
			
				rs.close();
			}

			
			if(isDt) {
				JSONObject dt  = new JSONObject();
				dt.put("data", ja);
				response = Response.ok(dt.toString()).build();
			} else {
				response = Response.ok(ja.toString()).build();
			}
			
			
			
			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			try {if (pstmtGetMainForm != null) {pstmtGetMainForm.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetForm != null) {pstmtGetForm.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetData != null) {pstmtGetData.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetSimilar != null) {pstmtGetSimilar.close();	}} catch (SQLException e) {	}
			
			ResultsDataSource.closeConnection("koboToolboxApi - get data records", cResults);			
			SDDataSource.closeConnection("koboToolboxApi - get data records", sd);
		}
		
		return response;
		
	}
	
	/*
	 * Convert the human name for the sort column into sql
	 */
	private String getSortColumn(ArrayList<Column> columns, String sort) {
		String col = "prikey";	// default to prikey
		sort = sort.trim();
		for(int i = 0; i < columns.size(); i++) {
			if(columns.get(i).humanName.equals(sort)) {
				Column c = columns.get(i);

				if(c.isCalculate()) {
					col = c.calculation;
				} else {
					col = c.name;
				}
				break;
			}
		}
		return col;
	}
	
	
}

