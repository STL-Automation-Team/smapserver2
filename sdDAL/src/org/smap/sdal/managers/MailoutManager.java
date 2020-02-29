package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.internet.InternetAddress;

import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.EmailServer;
import org.smap.sdal.model.EmailTaskMessage;
import org.smap.sdal.model.Mailout;
import org.smap.sdal.model.MailoutMessage;
import org.smap.sdal.model.MailoutPerson;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.SubscriptionStatus;
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

/*
 * Manage the log table
 * Assume emails are case insensitive
 */
public class MailoutManager {
	
	private static Logger log =
			 Logger.getLogger(MailoutManager.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	ResourceBundle localisation = null;
	
	public MailoutManager(ResourceBundle l) {
		localisation = l;	
	}

	public static String STATUS_NEW = "new";
	public static String STATUS_SENT = "sent";
	public static String STATUS_UNSUBSCRIBED = "unsubscribed";
	public static String STATUS_PENDING = "pending";
	public static String STATUS_ERROR = "error";
	
	/*
	 * Get mailouts for a survey
	 */
	public ArrayList<Mailout> getMailouts(Connection sd, String surveyIdent) throws SQLException {
		
		ArrayList<Mailout> mailouts = new ArrayList<> ();
		
		String sql = "select id, survey_ident, name "
				+ "from mailout "
				+ "where survey_ident = ?";
		
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, surveyIdent);
			log.info("Get mailouts: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				mailouts.add(new Mailout(
						rs.getInt("id"),
						rs.getString("survey_ident"), 
						rs.getString("name")));
				
			}
		
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
		}
		
		return mailouts;
	}
	
	/*
	 * Add a mailout
	 */
	public void addMailout(Connection sd, Mailout mailout) throws SQLException, ApplicationException {
		
		String sql = "insert into mailout "
				+ "(survey_ident, name, created, modified) "
				+ "values(?, ?, now(), now())";
		
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1,  mailout.survey_ident);
			pstmt.setString(2, mailout.name);
			log.info("Add mailout: " + pstmt.toString());
			pstmt.executeUpdate();
		
		} catch(Exception e) {
			String msg = e.getMessage();
			if(msg != null && msg.contains("duplicate key value violates unique constraint")) {
				throw new ApplicationException(localisation.getString("msg_dup_name"));
			} else {
				throw e;
			}
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
		}
	}
	
	/*
	 * Get mailouts Details
	 */
	public Mailout getMailoutDetails(Connection sd, int mailoutId) throws SQLException {
		
		Mailout mailout = null;
		
		String sql = "select survey_ident, name "
				+ "from mailout "
				+ "where id = ?";
		
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, mailoutId);
			log.info("Get mailout details: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				mailout = new Mailout(
						mailoutId,
						rs.getString("survey_ident"), 
						rs.getString("name"));
				
			}
		
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
		}
		
		return mailout;
	}
	
	/*
	 * Get mailouts Details
	 */
	public ArrayList<MailoutPerson> getMailoutPeople(Connection sd, int mailoutId) throws SQLException {
		
		ArrayList<MailoutPerson> mp = new ArrayList<> ();
		
		String sql = "select mp.id, p.name, p.email, mp.status "
				+ "from mailout_people mp, people p "
				+ "where p.id = mp.p_id "
				+ "and mp.m_id = ? "
				+ "order by p.email asc ";
		
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, mailoutId);
			log.info("Get mailout people: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()) {
				mp.add(new MailoutPerson(
						rs.getInt("id"),
						rs.getString("email"), 
						rs.getString("name"),
						rs.getString("status")));			
			}
		
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
		}
		
		return mp;
	}
	
	public void deleteUnsentEmails(Connection sd, int mailoutId) throws SQLException {
		
		String sql = "delete from mailout_people "
				+ "where m_id = ? "
				+ "and status = ? ";
		
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, mailoutId);
			pstmt.setString(2, STATUS_NEW);
			log.info("Delete unsent: " + pstmt.toString());
			pstmt.executeUpdate();	
		
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
		}
	}
	
public void writeEmails(Connection sd, int oId, ArrayList<MailoutPerson> mop, int mailoutId) throws Exception {
		
		String sqlGetPerson = "select id from people "
				+ "where o_id = ? "
				+ "and email = ? ";		
		PreparedStatement pstmtGetPerson = null;
		
		String sqlAddPerson = "insert into people "
				+ "(o_id, email, name) "
				+ "values(?, ?, ?)";		
		PreparedStatement pstmtAddPerson = null;
		
		String sqlAddMailoutPerson = "insert into mailout_people "
				+ "(p_id, m_id, status) "
				+ "values(?, ?, 'new') ";
		PreparedStatement pstmtAddMailoutPerson = null;
		
		try {
			pstmtGetPerson = sd.prepareStatement(sqlGetPerson);
			pstmtGetPerson.setInt(1, oId);
			
			pstmtAddPerson = sd.prepareStatement(sqlAddPerson, Statement.RETURN_GENERATED_KEYS);
			pstmtAddPerson.setInt(1, oId);
			
			pstmtAddMailoutPerson = sd.prepareStatement(sqlAddMailoutPerson);
			
			for(MailoutPerson person : mop) {
				
				int personId = 0;
				
				// 1. Get person details
				pstmtGetPerson.setString(2, person.email);
				ResultSet rs = pstmtGetPerson.executeQuery();
				if(rs.next()) {
					personId = rs.getInt(1);
				} else {
					
					// 2. Add person to people table if they do not exist
					pstmtAddPerson.setString(2, person.email);
					pstmtAddPerson.setString(3, person.name);
					pstmtAddPerson.executeUpdate();
					ResultSet rsKeys = pstmtAddPerson.getGeneratedKeys();
					if(rsKeys.next()) {
						personId = rsKeys.getInt(1);
					} else {
						throw new Exception("Failed to get id of person");
					}
				}
				
				// Write the entry into the mailout person table
				pstmtAddMailoutPerson.setInt(1, personId);
				pstmtAddMailoutPerson.setInt(2, mailoutId);
				
				pstmtAddMailoutPerson.executeUpdate();
			}
			
	
		} finally {
			try {if (pstmtGetPerson != null) {pstmtGetPerson.close();} } catch (SQLException e) {	}
			try {if (pstmtAddPerson != null) {pstmtAddPerson.close();} } catch (SQLException e) {	}
			try {if (pstmtAddMailoutPerson != null) {pstmtAddMailoutPerson.close();} } catch (SQLException e) {	}
		}
	}

	/*
	 * Send emails for a mailout
	 */
	public void sendEmails(Connection sd, int mailoutId) throws SQLException {
		
		String sql = "update mailout_people set status = '" + MailoutManager.STATUS_PENDING +"'  "
				+ "where m_id = ? "
				+ "and status = '" + MailoutManager.STATUS_NEW +"'";
		
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, mailoutId);
			log.info("Send unsent: " + pstmt.toString());
			pstmt.executeUpdate();
			
		
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
		}
		
		return;
	}
	
	/*
	 * Send an email mailout message
	 */
	public void emailMailout(
			Connection sd, 
			Connection cResults, 
			Organisation organisation,
			MailoutMessage msg,
			int messageId,
			String user,
			String basePath,
			String scheme,
			String server,
			String topic,
			boolean createPending) throws Exception {
		
		String docURL = null;
		String filePath = null;
		String filename = "instance";
		int surveyId = GeneralUtilityMethods.getSurveyId(sd, msg.survey_ident);
		
		boolean writeToMonitor = true;
		MessagingManager mm = new MessagingManager(localisation);
		
		PreparedStatement pstmtNotificationLog = null;
		String sqlNotificationLog = "insert into notification_log " +
				"(o_id, p_id, s_id, notify_details, status, status_details, event_time, message_id, type) " +
				"values( ?, ?,?, ?, ?, ?, now(), ?, 'mailout'); ";
		
		// Time Zone
		int utcOffset = 0;	
		LocalDateTime dt = LocalDateTime.now();
		if(organisation.timeZone != null) {
			try {
				ZoneId zone = ZoneId.of(organisation.timeZone);
			    ZonedDateTime zdt = dt.atZone(zone);
			    ZoneOffset offset = zdt.getOffset();
			    utcOffset = offset.getTotalSeconds() / 60;
			} catch (Exception e) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		
		try {
			
			pstmtNotificationLog = sd.prepareStatement(sqlNotificationLog);
			
			// Notification log
			ArrayList<String> unsubscribedList  = null;
			String error_details = null;
			String notify_details = null;
			String status = null;
			boolean unsubscribed = false;
			
			if(organisation.email_task) {
				
				//docURL = "/webForm" + msg.actionLink;
					
				/*
				 * Send document to target
				 */
				status = "success";				// Notification log
				notify_details = null;			// Notification log
				error_details = null;				// Notification log
				unsubscribed = false;
				if(msg.target.equals("email")) {
					EmailServer emailServer = UtilityMethodsEmail.getSmtpHost(sd, null, msg.user);
					if(emailServer.smtpHost != null && emailServer.smtpHost.trim().length() > 0) {
						if(UtilityMethodsEmail.isValidEmail(msg.email)) {
								
							log.info("userevent: " + msg.user + " sending email of '" + docURL + "' to " + msg.email);
							
							// Set the subject
							String subject = "";
							if(msg.subject != null && msg.subject.trim().length() > 0) {
								subject = msg.subject;
							} else {
								if(server != null && server.contains("smap")) {
									subject = "Smap ";
								}
								subject += localisation.getString("c_notify");
							}
							
							String from = "smap";
							if(msg.from != null && msg.from.trim().length() > 0) {
								from = msg.from;
							}
							String content = null;
							if(msg.content != null && msg.content.trim().length() > 0) {
								content = msg.content;
							} else {
								content = organisation.default_email_content;
							}
							
							notify_details = "Sending task email to: " + msg.email + " containing link " + docURL;
							
							log.info("+++ emailing mailout to: " + msg.email + " docUrl: " + docURL + 
									" from: " + from + 
									" subject: " + subject +
									" smtp_host: " + emailServer.smtpHost +
									" email_domain: " + emailServer.emailDomain);
							try {
								EmailManager em = new EmailManager();
								PeopleManager peopleMgr = new PeopleManager(localisation);
								InternetAddress[] emailArray = InternetAddress.parse(msg.email);
								
								for(InternetAddress ia : emailArray) {	
									SubscriptionStatus subStatus = peopleMgr.getEmailKey(sd, organisation.id, ia.getAddress());							
									if(subStatus.unsubscribed) {
										unsubscribed = true;
										setMailoutStatus(sd, msg.mpId, STATUS_UNSUBSCRIBED, null);
									} else {
										if(subStatus.optedIn || !organisation.send_optin) {
											log.info("Send email: " + msg.email + " : " + docURL);
											em.sendEmail(
													ia.getAddress(), 
													null, 
													"notify", 
													subject, 
													content,
													from,		
													null, 
													null, 
													null, 
													docURL, 
													filePath,
													filename,
													organisation.getAdminEmail(), 
													emailServer,
													scheme,
													server,
													subStatus.emailKey,
													localisation,
													organisation.server_description,
													organisation.name);
											setMailoutStatus(sd, msg.mpId, STATUS_SENT, null);
										
										} else {
											/*
											 * User needs to opt in before email can be sent
											 * Move message to pending messages and send opt in message if needed
											 */ 
											mm.saveToPending(sd, organisation.id, ia.getAddress(), topic, null, 
													null,
													msg, 
													subStatus.optedInSent,
													organisation.getAdminEmail(),
													emailServer,
													subStatus.emailKey,
													createPending,
													scheme,
													server);
										}
									}
								}
							} catch(Exception e) {
								status = "error";
								error_details = e.getMessage();
								setMailoutStatus(sd, msg.mpId, "error", error_details);
							}
						} else {
							log.log(Level.INFO, "Info: List of email recipients is empty");
							lm.writeLog(sd, surveyId, "subscriber", LogManager.EMAIL, localisation.getString("email_nr"));
							writeToMonitor = false;
						}
					} else {
						status = "error";
						error_details = "smtp_host not set";
						log.log(Level.SEVERE, "Error: Attempt to do email notification but email server not set");
					}
					
				}  else {
					status = "error";
					error_details = "Invalid target: " + msg.target;
					log.log(Level.SEVERE, "Error: Invalid target" + msg.target);
				}
			} else {
				status = "error";
				error_details = localisation.getString("susp_email_task");
				log.log(Level.SEVERE, "Error: notification services suspended");
			}
			
			// Write log message
			if(writeToMonitor) {
				if(unsubscribed) {
					error_details += localisation.getString("c_unsubscribed") + ": " + msg.email;
				}
				pstmtNotificationLog.setInt(1, organisation.id);
				pstmtNotificationLog.setInt(2, msg.pId);
				pstmtNotificationLog.setInt(3, surveyId);
				pstmtNotificationLog.setString(4, notify_details);
				pstmtNotificationLog.setString(5, status);
				pstmtNotificationLog.setString(6, error_details);
				pstmtNotificationLog.setInt(7, messageId);
				
				pstmtNotificationLog.executeUpdate();
			}
		} finally {
			try {if (pstmtNotificationLog != null) {pstmtNotificationLog.close();}} catch (SQLException e) {}
			
		}
	}
	
	private void setMailoutStatus(Connection sd, int mpId, String status, String details) throws SQLException {
		
		String sql = "update mailout_people "
				+ "set status = ?,"
				+ "status_details = ? "
				+ "where id = ? ";
		
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, status);
			pstmt.setString(2, details);
			pstmt.setInt(3, mpId);
			pstmt.executeUpdate();
			
		
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
		}
	}
	
}


