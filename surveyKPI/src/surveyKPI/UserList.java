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
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.Project;
import org.smap.sdal.model.Role;
import org.smap.sdal.model.User;
import org.smap.sdal.model.UserGroup;

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
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		String sql = "select id,"
				+ "ident, "
				+ "name, "
				+ "email, "
				+ "o_id "
				+ "from users "
				+ "where (users.o_id = ? or id in (select uo.u_id from user_organisation uo where uo.o_id = users.o_id)) "
				+ "and not users.temporary "
				+ "order by ident asc";
		PreparedStatement pstmt = null;
		
		String sqlGroups = "select g.id,"
				+ "g.name "
				+ "from groups g,"
				+ "user_group ug "
				+ "where ug.u_id = ? "
				+ "and ug.g_id = g.id "
				+ "order by g.id asc";
		PreparedStatement pstmtGroups = null;
		
		String sqlProjects = "select p.id,"
				+ "p.name "
				+ "from project p,"
				+ "user_project up "
				+ "where up.u_id = ? "
				+ "and up.p_id = p.id "
				+ "order by p.name asc";
		PreparedStatement pstmtProjects = null;
		
		String sqlRoles = "select r.id,"
				+ "r.name "
				+ "from role r,"
				+ "user_role ur "
				+ "where ur.u_id = ? "
				+ "and ur.r_id = r.id "
				+ "order by r.name asc";
		PreparedStatement pstmtRoles = null;
		
		String sqlOrgs = "select o.id,"
				+ "o.name "
				+ "from organisation o,"
				+ "user_organisation uo "
				+ "where uo.u_id = ? "
				+ "and uo.o_id = o.id "
				+ "order by o.name asc";
		PreparedStatement pstmtOrgs = null;
		
		String sqlGetSavedUser = "select settings "
				+ "from user_organisation uo "
				+ "where o_id = ? "
				+ "and u_id = ?";
		PreparedStatement pstmtGetSavedUser = null;
		
		ArrayList<User> users = new ArrayList<User> ();
		Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		
		try {
			int o_id = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);
			boolean isOrgUser = GeneralUtilityMethods.isOrgUser(sd, request.getRemoteUser());
			boolean isSecurityManager = GeneralUtilityMethods.hasSecurityRole(sd, request.getRemoteUser());
			
			pstmt = sd.prepareStatement(sql);
			ResultSet rs = null;

			pstmtGroups = sd.prepareStatement(sqlGroups);
			ResultSet rsGroups = null;
			
			pstmtProjects = sd.prepareStatement(sqlProjects);
			ResultSet rsProjects = null;
			
			pstmtRoles = sd.prepareStatement(sqlRoles);
			ResultSet rsRoles = null;
			
			pstmtOrgs = sd.prepareStatement(sqlOrgs);
			ResultSet rsOrgs = null;
			
			pstmt.setInt(1, o_id);
			log.info("Get user list: " + pstmt.toString());
			rs = pstmt.executeQuery();
			while(rs.next()) {
				int usersOrgId = rs.getInt("o_id");
				User user = null;
				int uId = rs.getInt("id");

				if(usersOrgId != o_id) {
					// User is not currently in this organisation
					pstmtGetSavedUser = sd.prepareStatement(sqlGetSavedUser);
					pstmtGetSavedUser.setInt(1, o_id);
					pstmtGetSavedUser.setInt(2, uId);
					log.info("Get saved user details: " + pstmtGetSavedUser.toString());
					ResultSet rs2 = pstmtGetSavedUser.executeQuery();
					if(rs2.next()) {
						user = gson.fromJson(rs2.getString(1), User.class);
					}
				} else {
					// Current user in the same organisation as the administrator
					user = new User();
				
					user.id = rs.getInt("id");
					user.ident = rs.getString("ident");
					user.name = rs.getString("name");
					user.email = rs.getString("email");
					
					// Groups
					if(rsGroups != null) try {rsGroups.close();} catch(Exception e) {};
					pstmtGroups.setInt(1, user.id);
					rsGroups = pstmtGroups.executeQuery();
					user.groups = new ArrayList<UserGroup> ();
					while(rsGroups.next()) {
						UserGroup ug = new UserGroup();
						ug.id = rsGroups.getInt("id");
						ug.name = rsGroups.getString("name");
						user.groups.add(ug);
					}
					
					// Projects
					if(rsProjects != null) try {rsProjects.close();} catch(Exception e) {};
					pstmtProjects.setInt(1, user.id);
					rsProjects = pstmtProjects.executeQuery();
					user.projects = new ArrayList<Project> ();
					while(rsProjects.next()) {
						Project p = new Project();
						p.id = rsProjects.getInt("id");
						p.name = rsProjects.getString("name");
						user.projects.add(p);
					}
					
					// Roles
					if(isOrgUser || isSecurityManager) {
						if(rsRoles != null) try {rsRoles.close();} catch(Exception e) {};
						pstmtRoles.setInt(1, user.id);
						rsRoles = pstmtRoles.executeQuery();
						user.roles = new ArrayList<Role> ();
						while(rsRoles.next()) {
							Role r = new Role();
							r.id = rsRoles.getInt("id");
							r.name = rsRoles.getString("name");
							user.roles.add(r);
						}
					}
					
					// Organisations
					if(isOrgUser) {
						if(rsOrgs != null) try {rsOrgs.close();} catch(Exception e) {};
						pstmtOrgs.setInt(1, user.id);
						rsOrgs = pstmtOrgs.executeQuery();
						user.orgs = new ArrayList<Organisation> ();
						while(rsOrgs.next()) {
							Organisation o = new Organisation();
							o.id = rsOrgs.getInt("id");
							o.name = rsOrgs.getString("name");
							user.orgs.add(o);
						}
						if(user.orgs.size() == 0) {
							/*
							 * Add a default organisation equal to the users current organisation
							 * This is only needed for users who were created before organisation linking was added
							 */
							Organisation o = new Organisation();
							o.id = o_id;
							user.orgs.add(o);
						}
					}
				} 
				
				if(user != null) {
					users.add(user);
				}
			}
			
			String resp = gson.toJson(users);
			response = Response.ok(resp).build();
						
			
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
			response = Response.serverError().entity(e.getMessage()).build();
		    
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (Exception e) {	}
			try {if (pstmtGroups != null) {pstmtGroups.close();	}} catch (Exception e) {	}
			try {if (pstmtProjects != null) {pstmtProjects.close();	}} catch (Exception e) {	}
			try {if (pstmtRoles != null) {pstmtRoles.close();	}} catch (Exception e) {	}
			try {if (pstmtOrgs != null) {pstmtOrgs.close();	}} catch (Exception e) {}
			try {if (pstmtGetSavedUser != null) {pstmtGetSavedUser.close();	}} catch (Exception e) {}
			SDDataSource.closeConnection(requestName, sd);
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
			
			ActionManager am = new ActionManager(localisation);
			
			int o_id = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);
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
			int o_id = 0;
			
			/*
			 * Get the organisation
			 */
			sql = "SELECT u.o_id " +
					" FROM users u " +  
					" WHERE u.ident = ?;";				
						
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, request.getRemoteUser());
			log.info("Get organisation: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
			}
			
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
			
			if(pstmt != null) try {pstmt.close();}catch(Exception e) {}
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
	 * Update the settings or create new user
	 */
	@POST
	@Consumes("application/json")
	public Response updateUser(@Context HttpServletRequest request, @FormParam("users") String users) { 
		
		Response response = null;
		String requestName = "surveyKPI - updateUser";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(requestName);
		aUpdate.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		Type type = new TypeToken<ArrayList<User>>(){}.getType();		
		ArrayList<User> uArray = new Gson().fromJson(users, type);
		
		PreparedStatement pstmt = null;
		try {	
			
			ResourceBundle localisation = null;
			String sql = null;
			int o_id;
			String adminName = null;
			String language;	// Language spoken by user
			ResultSet resultSet = null;
			boolean isOrgUser = GeneralUtilityMethods.isOrgUser(sd, request.getRemoteUser());
			boolean isSecurityManager = GeneralUtilityMethods.hasSecurityRole(sd, request.getRemoteUser());
			
			/*
			 * Get the organisation and name of the user making the request
			 */
			sql = "SELECT u.o_id, u.name, u.language " +
					" FROM users u " +  
					" WHERE u.ident = ?;";				
						
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, request.getRemoteUser());
			log.info("SQL: " + sql + ":" + request.getRemoteUser());
			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
				adminName = resultSet.getString(2);
				language = resultSet.getString(3);
				
				// Set locale
				if(language == null) {
					language = "en";
				}
				localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", new Locale(language));
				
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
								request.getRemoteUser(),
								request.getScheme(),
								request.getServerName(),
								adminName,
								localisation);
	
						lm.writeLogOrganisation(sd, 
								o_id, request.getRemoteUser(), "create", "User " + u.ident + " was created");
								
					} else {
						// Existing user
						um.updateUser(sd, u, o_id,
								isOrgUser,
								isSecurityManager,
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
		PreparedStatement pstmtUpdate = null;
		PreparedStatement pstmtGetIdent = null;
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
			
			// Get the ident of the person to be deleted
			String sqlGetIdent = "select u.ident "
					+ "from users u "
					+ "where u.id = ? "
					+ "and u.o_id = ?";								
			pstmtGetIdent = sd.prepareStatement(sqlGetIdent);
			
			// Delete the user
			String sqlUpdate = "DELETE FROM users u " +  
					" WHERE u.id = ? " +			// Ensure the user is in the same organisation as the administrator doing the editing
					" AND u.o_id = ?;";					
			pstmtUpdate = sd.prepareStatement(sqlUpdate);
			
			// Get the organisation id
			pstmt.setString(1, request.getRemoteUser());
			log.info("Get user organisation and id: " + pstmt.toString());			
			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
				
				for(int i = 0; i < uArray.size(); i++) {
					User u = uArray.get(i);
					String ident = null;
					
					// Get the user ident to use in deleting dependent records
					pstmtGetIdent.setInt(1, u.id);
					pstmtGetIdent.setInt(2,o_id);
					ResultSet rs = pstmtGetIdent.executeQuery();
					if(rs.next()) {
						ident = rs.getString(1);
					}
					
					// Peform the delete
					pstmtUpdate.setInt(1, u.id);
					pstmtUpdate.setInt(2, o_id);
					log.info("Delete user: " + pstmt.toString());
					
					int count = pstmtUpdate.executeUpdate();
					
					if(count > 0) {	
						// If a user was deleted then delete their directories
						GeneralUtilityMethods.deleteDirectory(basePath + "/media/users/" + u.id);
						
						// Delete any csv table definitions that they have
						SurveyTableManager stm = new SurveyTableManager(sd, localisation);
						stm.deleteForUsers(ident);			// Delete references to this survey in the csv table 
					}	

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
			try {if (pstmtUpdate != null) {pstmtUpdate.close();}} catch (SQLException e) {}
			try {if (pstmtGetIdent != null) {pstmtGetIdent.close();}} catch (SQLException e) {}
			
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

