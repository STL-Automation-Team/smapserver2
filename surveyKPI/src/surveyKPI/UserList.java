package surveyKPI;

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
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.ActionManager;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.MessagingManager;
import org.smap.sdal.managers.SurveyTableManager;
import org.smap.sdal.managers.UserManager;
import org.smap.sdal.model.User;
import org.smap.sdal.model.UserGroup;
import org.smap.sdal.model.UserSimple;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Returns a list of all users that are in the same organisation as the user making the request
 */
@Path("/userList")
public class UserList extends Application {
	
	Authorise a = null;
	Authorise aUpdate = null;
	Authorise aSM = null;
	Authorise aSimpleList = null;

	private static Logger log =
			 Logger.getLogger(UserList.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	public UserList() {
		
		// Allow administrators and analysts to view the list of users
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		authorisations.add(Authorise.SECURITY);
		authorisations.add(Authorise.ORG);
		a = new Authorise(authorisations, null);
		
		// Also allow users with View rights to view the simple list of users
		authorisations.add(Authorise.VIEW_DATA);
		authorisations.add(Authorise.MANAGE);
		aSimpleList = new Authorise(authorisations, null);
		
		// Only allow administrators, org administrators and security managers to update user list
		authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ADMIN);
		authorisations.add(Authorise.SECURITY);
		authorisations.add(Authorise.ORG);
		aUpdate = new Authorise(authorisations, null);
		
		// Only allow security administrators and organisational administrators to view or update the roles
		authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.SECURITY);
		authorisations.add(Authorise.ORG);
		aSM = new Authorise(authorisations, null);
		
	}
	
	@GET
	@Produces("application/json")
	public Response getUsers(
			@Context HttpServletRequest request
			) { 

		Response response = null;
		String requestName = "surveyKPI-getUsers";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(requestName);
		aSimpleList.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		ArrayList<User> users = null;
		Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		
		try {
			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);	
			
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			boolean isOrgUser = GeneralUtilityMethods.isOrgUser(sd, request.getRemoteUser());
			boolean isSecurityManager = GeneralUtilityMethods.hasSecurityRole(sd, request.getRemoteUser());
			
			UserManager um = new UserManager(localisation);
			users = um.getUserList(sd, oId, isOrgUser, isSecurityManager);
			String resp = gson.toJson(users);
			response = Response.ok(resp).build();
						
			
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
			response = Response.serverError().entity(e.getMessage()).build();
		    
		} finally {
			
			SDDataSource.closeConnection(requestName, sd);
		}

		return response;
	}
	
	@GET
	@Produces("application/json")
	@Path("/simple")
	public Response getUsersSimple(
			@Context HttpServletRequest request
			) { 

		Response response = null;
		String connectionString = "surveyKPI-getUsers";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		aSimpleList.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		ArrayList<UserSimple> users = null;
		Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		
		try {
			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);	
			
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			boolean isOnlyViewData = GeneralUtilityMethods.isOnlyViewData(sd, request.getRemoteUser());
			UserManager um = new UserManager(localisation);
			users = um.getUserListSimple(sd, oId, true, isOnlyViewData, request.getRemoteUser());		// Always sort by name
			String resp = gson.toJson(users);
			response = Response.ok(resp).build();
						
			
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
			response = Response.serverError().entity(e.getMessage()).build();
		    
		} finally {
			
			SDDataSource.closeConnection(connectionString, sd);
		}

		return response;
	}


	@GET
	@Path("/temporary")
	@Produces("application/json")
	public Response getTemporaryUsers(
			@Context HttpServletRequest request,
			@QueryParam("action") String action,
			@QueryParam("pId") int pId
			) { 

		Response response = null;
		String requestName = "surveyKPI - getTemporaryUsers";

		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(requestName);
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation			
		
		try {
			// Localisation			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String tz = "UTC";	// Set default for timezone
			
			ActionManager am = new ActionManager(localisation, tz);
			
			int o_id = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			
			ArrayList<User> users = am.getTemporaryUsers(sd, o_id, action, 0, pId);			
			String resp = gson.toJson(users);
			response = Response.ok(resp).build();			
			
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
			response = Response.serverError().entity(e.getMessage()).build();
		    
		} finally {
		
			SDDataSource.closeConnection(requestName, sd);
		}

		return response;
	}

	
	
	/*
	 * Get the users who have access to a specific project
	 */
	@Path("/{projectId}")
	@GET
	@Produces("application/json")
	public Response getUsersForProject(
			@Context HttpServletRequest request,
			@PathParam("projectId") int projectId
			) { 

		Response response = null;
		String requestName = "surveyKPI - getUsersForProject";

		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(requestName);
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidProject(sd, request.getRemoteUser(), projectId);
		// End Authorisation
		
		/*
		 * 
		 */	
		PreparedStatement pstmt = null;
		ArrayList<User> users = new ArrayList<User> ();
		
		try {
			String sql = null;
			ResultSet resultSet = null;
			int o_id = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			
			/*
			 * Get the users for this project
			 */
			sql = "SELECT u.id as id, " +
					"u.ident as ident, " +
					"u.name as name, " +
					"u.email as email " +
					"from users u, user_project up " +			
					"where u.id = up.u_id " +
					"and up.p_id = ? " +
					"and u.o_id = ? " +
					"and not u.temporary " +
					"order by u.ident";
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, projectId);
			pstmt.setInt(2, o_id);
			log.info("Get users for project: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
							
			User user = null;
			while(resultSet.next()) {
				user = new User();
				user.id = resultSet.getInt("id");
				user.ident = resultSet.getString("ident");
				user.name = resultSet.getString("name");
				user.email = resultSet.getString("email");
				users.add(user);
			}
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(users);
			response = Response.ok(resp).build();
					
				
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
			response = Response.serverError().entity(e.getMessage()).build();
		    
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			SDDataSource.closeConnection(requestName, sd);
		}

		return response;
	}
	
	/*
	 * Get the users who have access to a specific survey
	 */
	@Path("/survey/{survey}")
	@GET
	@Produces("application/json")
	public Response getUsersForSurvey(
			@Context HttpServletRequest request,
			@PathParam("survey") int sId
			) { 

		Response response = null;
		String requestName = "surveyKPI - getUsersForSurvey";

		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(requestName);
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}		
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation
		
		PreparedStatement pstmt = null;
		ArrayList<UserSimple> users = new ArrayList<> ();
		
		try {
			ResultSet resultSet = null;
			
			StringBuffer sql = new StringBuffer("select u.id, u.ident, u.name from survey s, users u, user_project up, project p "
					+ "where u.id = up.u_id "
					+ "and p.id = up.p_id "
					+ "and s.p_id = up.p_id "
					+ "and s.s_id = ? "
					+ "and not temporary");
			String sqlRBAC = " and ((s.s_id not in (select s_id from survey_role where enabled = true)) or " // No roles on survey
					+ "(s.s_id in (select s_id from users u2, user_role ur, survey_role sr where u2.ident = u.ident and sr.enabled = true and u.id = ur.u_id and ur.r_id = sr.r_id)) " // User also has role
					+ ") ";
			
			sql.append(sqlRBAC);
			
			pstmt = sd.prepareStatement(sql.toString());
			pstmt.setInt(1, sId);
			log.info("Get users of survey: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
							
			UserSimple user = null;
			while(resultSet.next()) {
				user = new UserSimple();
				user.id = resultSet.getInt("id");
				user.ident = resultSet.getString("ident");
				user.name = resultSet.getString("name");
				users.add(user);
			}
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(users);
			response = Response.ok(resp).build();
					
				
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
			response = Response.serverError().entity(e.getMessage()).build();
		    
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			SDDataSource.closeConnection(requestName, sd);
		}

		return response;
	}
	
	/*
	 * Update the settings or create new user
	 */
	@POST
	@Consumes("application/json")
	public Response updateUser(@Context HttpServletRequest request, @FormParam("users") String users) { 
		
		Response response = null;
		String requestName = "surveyKPI - updateUser";
		
		Type type = new TypeToken<ArrayList<User>>(){}.getType();		
		ArrayList<User> uArray = new Gson().fromJson(users, type);
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(requestName);
		aUpdate.isAuthorised(sd, request.getRemoteUser());
		
		// That the user is in the administrators organisation is validated on update
		
		// End Authorisation
	
		PreparedStatement pstmt = null;
		try {	
			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String sql = null;
			int o_id;
			String adminName = null;
			ResultSet resultSet = null;
			boolean isOrgUser = GeneralUtilityMethods.isOrgUser(sd, request.getRemoteUser());
			boolean isSecurityManager = GeneralUtilityMethods.hasSecurityRole(sd, request.getRemoteUser());
			boolean isEnterpriseManager = GeneralUtilityMethods.isEntUser(sd, request.getRemoteUser());
			boolean isServerOwner = GeneralUtilityMethods.isServerOwner(sd, request.getRemoteUser());
			
			/*
			 * Get the organisation and name of the user making the request
			 */
			sql = "SELECT u.o_id, u.name " +
					" FROM users u " +  
					" WHERE u.ident = ?";				
						
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, request.getRemoteUser());
			log.info("SQL: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
				adminName = resultSet.getString(2);			
				
				for(int i = 0; i < uArray.size(); i++) {
					User u = uArray.get(i);
					
					// Ensure email is null if it has not been set
					if(u.email != null && u.email.trim().isEmpty()) {
						u.email = null;
					}
					
					UserManager um = new UserManager(localisation);
					if(u.id == -1) {
						// New user
						um.createUser(sd, u, o_id,
								isOrgUser,
								isSecurityManager,
								isEnterpriseManager,
								isServerOwner,
								request.getRemoteUser(),
								request.getScheme(),
								request.getServerName(),
								adminName,
								localisation);
								
					} else {
						// Existing user
						um.updateUser(sd, u, o_id,
								isOrgUser,
								isSecurityManager,
								isEnterpriseManager,
								isServerOwner,
								request.getRemoteUser(),
								request.getServerName(),
								adminName,
								false);
						
						lm.writeLogOrganisation(sd, 
								o_id, request.getRemoteUser(), "Update", "User " + u.ident + " was updated. Groups: " + getGroups(u.groups));
					}
					
					// Record the user change so that devices can be notified
					MessagingManager mm = new MessagingManager();
					mm.userChange(sd, u.ident);
				}

				
				response = Response.ok().build();
			} else {
				log.log(Level.SEVERE,"Error: No organisation");
				response = Response.serverError().entity("Error: No organisation").build();
			}
				
		} catch (SQLException e) {
			
			String state = e.getSQLState();
			log.info("sql state:" + state);
			if(state.startsWith("23")) {
				response = Response.status(Status.CONFLICT).entity(e.getMessage()).build();
				log.log(Level.SEVERE,"Error", e);
			} else {
				response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
				log.log(Level.SEVERE,"Error", e);
			}
		} catch (Exception e) {
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection(requestName, sd);
		}
		
		return response;
	}
	

	
	/*
	 * Delete users
	 */
	@DELETE
	@Consumes("application/json")
	public Response delUser(@Context HttpServletRequest request, @FormParam("users") String users) { 
		
		Response response = null;
		String requestName = "surveyKPI - delUser";

		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(requestName);
		aUpdate.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		Type type = new TypeToken<ArrayList<User>>(){}.getType();		
		ArrayList<User> uArray = new Gson().fromJson(users, type);
		
		PreparedStatement pstmt = null;
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		try {	
			int o_id;
			ResultSet resultSet = null;
			
			// Localisation			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			// Get the organisation of the person calling this service
			String sql = "SELECT u.o_id " +
					" FROM users u " +  
					" WHERE u.ident = ?;";										
			pstmt = sd.prepareStatement(sql);	
			
			// Get the organisation id
			pstmt.setString(1, request.getRemoteUser());
			log.info("Get user organisation and id: " + pstmt.toString());			
			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
							
				UserManager um = new UserManager(localisation);
				
				for(int i = 0; i < uArray.size(); i++) {
					User u = uArray.get(i);					
					um.deleteUser(sd, request.getRemoteUser(), 
							basePath, u.id, o_id, u.all);
				}
		
				response = Response.ok().build();
			} else {
				log.log(Level.SEVERE,"Error: No organisation");
			    response = Response.serverError().build();
			}
				
		} catch (SQLException e) {
			String state = e.getSQLState();
			log.info("sql state:" + state);
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
			
		} catch (Exception e) {
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
			
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection(requestName, sd);
		}
		
		return response;
	}
	

	
	private String getGroups(ArrayList<UserGroup> groups) {
		StringBuffer g = new StringBuffer("");
		if(groups != null) {
			for(UserGroup ug : groups) {
				String name = null;
				switch(ug.id) {
					case 1: name = "admin";
							break;
					case 2: name = "analyst";
							break;
					case 3: name = "enum";
							break;
					case 4: name = "org admin";
							break;
					case 5: name = "manage";
							break;
					case 6: name = "security";
							break;
					case 7: name = "view data";
							break;
				}
				if(name != null) {
					if(g.length() > 0) {
						g.append(", ");
					}
					g.append(name);
				}
				
			}
		}
		return g.toString();
	}

}

