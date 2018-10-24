package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.ForeignKey;
import org.smap.sdal.model.KeyValueSimp;

/*****************************************************************************
 * 
 * This file is part of SMAP.
 * 
 * SMAP is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * SMAP is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * SMAP. If not, see <http://www.gnu.org/licenses/>.
 * 
 ******************************************************************************/

/*
 * Manage the application of links between forms
 */
public class ForeignKeyManager {

	private static Logger log = Logger.getLogger(ForeignKeyManager.class.getName());


	public void saveKeys(Connection sd, String updateId, ArrayList<ForeignKey> keys, int sId) throws SQLException {
		
		String sql = "insert into apply_foreign_keys (id, update_id, s_id, qname, instanceid, "
				+ "prikey, table_name, ts_created) "
				+ "values (nextval('apply_foreign_keys_seq'), ?, ?, ?, ?, ?, ?, now())";
		PreparedStatement pstmt = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1,  updateId);
			pstmt.setInt(2, sId);
			for(ForeignKey fk : keys) {
				pstmt.setString(3,  fk.qName);
				pstmt.setString(4, fk.instanceId);
				pstmt.setInt(5,  fk.primaryKey);
				pstmt.setString(6, fk.tableName);
				log.info("Add pending foreign key: " + pstmt.toString());
				pstmt.executeUpdate();
			}
			
		} finally {
			if(pstmt != null) {try {pstmt.close();}catch(Exception e) {}}
		}
	}
	
	/*
	 * Apply foreign keys
	 */
	public void apply(Connection sd, Connection cResults) throws SQLException {
		
		String sql = "select id, s_id, qname, instanceid, prikey, table_name from apply_foreign_keys where not applied";
		PreparedStatement pstmt = null;
		
		String sqlForeign = "select parameters from question "
				+ "where f_id in (select f_id from form where s_id = ?) "
				+ "and qname = ?";
		PreparedStatement pstmtForeign = null;
		
		String sqlResult = "update apply_foreign_keys "
				+ "set applied = true,"
				+ "ts_applied = now(),"
				+ "comment = ? "
				+ "where id = ?";
		PreparedStatement pstmtResult = null;
		
		String sqlGetFkTable = "select table_name from form "
				+ "where s_id = ? "
				+ "and name = 'main'";				
		PreparedStatement pstmtGetFkTable = null;
		
		String sqlGetKeyQuestionTable = "select table_name from form "
				+ "where s_id = ? "
				+ "and f_id = (select f_id from question "
					+ "where f_id in (select f_id from form where s_id = ?) "
					+ "and qname = ?)";				
		PreparedStatement pstmtGetKeyQuestionTable = null;
		
		PreparedStatement pstmtGetHrk = null;
		PreparedStatement pstmtInsertKey = null;
		
		log.info("============================== Apply foreign keys");
		try {
			pstmtGetFkTable = sd.prepareStatement(sqlGetFkTable);
			pstmtResult = sd.prepareStatement(sqlResult);
			pstmtForeign = sd.prepareStatement(sqlForeign);
			
			pstmt = sd.prepareStatement(sql);
			log.info(pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				int id = rs.getInt(1);
				int sId = rs.getInt(2);
				String qname = rs.getString(3);
				String instanceid = rs.getString(4);
				int prikey = rs.getInt(5);
				String tableName = rs.getString(6);
				
				log.info("Found foreign key to apply: " + sId + " : " + qname + " : " + instanceid);
				// 1. Get the details on the foreign form and the question that will hold the foreign key
				pstmtForeign.setInt(1, sId);
				pstmtForeign.setString(2, qname);
				log.info("Get parameters: " + pstmtForeign.toString());
				ResultSet rsForeign = pstmtForeign.executeQuery();
				if(rsForeign.next()) {
					String parameters = rsForeign.getString(1);
					if(parameters != null) {
						ArrayList<KeyValueSimp> params = GeneralUtilityMethods.convertParametersToArray(parameters);
						String sIdent = null;
						String keyQuestion = null;
						for(KeyValueSimp p : params) {
							if(p.k.equals("form_identifier")) {
								sIdent  = p.v;
							} else if(p.k.equals("key_question")) {
								keyQuestion = p.v;
							}
						}
						if(keyQuestion == null) {
							pstmtResult.setString(1, "ok: Key question not specified");
							pstmtResult.setInt(2, id);
							pstmtResult.executeUpdate();
						} else if(sIdent == null) {
							pstmtResult.setString(1, "error: form identifier not found");
							pstmtResult.setInt(2, id);
							pstmtResult.executeUpdate();
						} else {
							int foreignSiD = GeneralUtilityMethods.getSurveyId(sd, sIdent);
							pstmtGetFkTable.setInt(1, foreignSiD);
							log.info("Get foreign key table: " + pstmtGetFkTable.toString());
							ResultSet rsGetFkTable = pstmtGetFkTable.executeQuery();
							if(rsGetFkTable.next()) {
								String foreignTable = rsGetFkTable.getString(1);
								String sqlGetHrk1 = "select prikey from " + foreignTable +
										" where instanceid=?";
								String sqlGetHrk2 = "select prikey, _hrk from " + foreignTable +
										" where instanceid=?";
								boolean hasHrk = GeneralUtilityMethods.hasColumn(cResults, foreignTable, "_hrk");
								if(hasHrk) {
									pstmtGetHrk = cResults.prepareStatement(sqlGetHrk2);
								} else {
									pstmtGetHrk = cResults.prepareStatement(sqlGetHrk1);
								}
								pstmtGetHrk.setString(1, instanceid);
								log.info("Get HRK: " + pstmtGetHrk.toString());
								ResultSet rsGetHrk = pstmtGetHrk.executeQuery();
								if(rsGetHrk.next()) {
									String key = rsGetHrk.getString(1);
									if(hasHrk) {
										key = rsGetHrk.getString(2);
									}
									log.info("------------ got foreign key details: " + key);
									
									/*
									 * Update the key question that contains the foreign key value
									 */										
									String keyColumnName = GeneralUtilityMethods.getColumnName(sd, sId, keyQuestion);
									
									String sqlInsertKey = "update " + tableName + " set " + keyColumnName +
											" = ? where prikey = ?";
									pstmtInsertKey = cResults.prepareStatement(sqlInsertKey);
									pstmtInsertKey.setString(1, key);
									pstmtInsertKey.setInt(2, prikey);
									log.info("Inserting key: " + pstmtInsertKey.toString());
									int count = pstmtInsertKey.executeUpdate();
									if(count == 0) {
										pstmtResult.setString(1, "error: failed to set key");
										pstmtResult.setInt(2, id);
										pstmtResult.executeUpdate();
									} else {
										pstmtResult.setString(1, "ok: applied");
										pstmtResult.setInt(2, id);
										pstmtResult.executeUpdate();
									}
									
								}
							} else {
								pstmtResult.setString(1, "error: foreign key table not found");
								pstmtResult.setInt(2, id);
								pstmtResult.executeUpdate();
							}
						}
					} else {
						pstmtResult.setString(1, "error: no parameters found");
						pstmtResult.setInt(2, id);
						pstmtResult.executeUpdate();
					}
				} else {
					pstmtResult.setString(1, "error: no parameters found");
					pstmtResult.setInt(2, id);
					pstmtResult.executeUpdate();
				}
			}
			log.info("------------------------------------ End Apply foreign keys");
			
		} finally {
			if(pstmt != null) {try {pstmt.close();}catch(Exception e) {}}
			if(pstmtForeign != null) {try {pstmtForeign.close();}catch(Exception e) {}}
			if(pstmtResult != null) {try {pstmtResult.close();}catch(Exception e) {}}
			if(pstmtGetHrk != null) {try {pstmtGetHrk.close();}catch(Exception e) {}}
			if(pstmtGetFkTable != null) {try {pstmtGetFkTable.close();}catch(Exception e) {}}
			if(pstmtGetKeyQuestionTable != null) {try {pstmtGetKeyQuestionTable.close();}catch(Exception e) {}}
			if(pstmtInsertKey != null) {try {pstmtInsertKey.close();}catch(Exception e) {}}
		}
	}
}