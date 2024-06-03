package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.Queue;
import org.smap.sdal.model.QueueItem;

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
 * This class supports access to the status of queues such as the submission queue 
 */
public class QueueManager {

	public String SUBMISSIONS = "submissions";
	public String RESTORE = "restore";
	public String S3UPLOAD = "s3upload";

	private static Logger log =
			Logger.getLogger(QueueManager.class.getName());
	/*
	 * Get status of submission queue
	 */
	public Queue getSubmissionQueueData(Connection sd) throws SQLException {

		PreparedStatement pstmtLength = null;
		PreparedStatement pstmtProcessedRate = null;
		PreparedStatement pstmtNewRate = null;

		Queue queue = new Queue();
		try {

			String sqlLength = "select count(*) "
					+ "from upload_event ue "
					+ "where ue.status = 'success' "
					+ "and ue.s_id is not null "
					+ "and not ue.incomplete "
					+ "and not ue.restore "
					+ "and not ue.results_db_applied ";
			pstmtLength = sd.prepareStatement(sqlLength);
			log.info("Get queue length: " + pstmtLength.toString());
			ResultSet rs = pstmtLength.executeQuery();
			if(rs.next()) {
				queue.length = rs.getInt(1);
			}

			String sqlProcessedRate = "select db_status, count(*) "
					+ "from upload_event ue "
					+ "where ue.status = 'success' "
					+ "and ue.s_id is not null "
					+ "and not ue.incomplete "
					+ "and not ue.restore "
					+ "and ue.processed_time > now() - interval '1 minute' "
					+ "group by db_status";
			pstmtProcessedRate = sd.prepareStatement(sqlProcessedRate);

			rs = pstmtProcessedRate.executeQuery();
			while(rs.next()) {
				String status = rs.getString(1);
				queue.processed_rpm += rs.getInt(2);	// Processed updated for all status values
				if(status != null) {
					if(status.equals("error")) {
						queue.error_rpm = rs.getInt(2);
					}
				}
			}

			String sqlNewRate = "select count(*) "
					+ "from upload_event ue "
					+ "where ue.status = 'success' "
					+ "and ue.s_id is not null "
					+ "and not ue.incomplete "
					+ "and not ue.restore "
					+ "and ue.upload_time > now() - interval '1 minute'";
			pstmtNewRate = sd.prepareStatement(sqlNewRate);

			rs = pstmtNewRate.executeQuery();
			if(rs.next()) {
				queue.new_rpm = rs.getInt(1);
			}


		} finally {
			try {if (pstmtLength != null) {pstmtLength.close();}} catch (SQLException e) {}
			try {if (pstmtProcessedRate != null) {pstmtProcessedRate.close();}} catch (SQLException e) {}
			try {if (pstmtNewRate != null) {pstmtNewRate.close();}} catch (SQLException e) {}
		}

		return queue;
	}
	
	/*
	 * Get status of restore queue
	 */
	public Queue getRestoreQueueData(Connection sd) throws SQLException {

		PreparedStatement pstmtLength = null;
		PreparedStatement pstmtProcessedRate = null;
		PreparedStatement pstmtNewRate = null;

		Queue queue = new Queue();
		try {

			String sqlLength = "select count(*) "
					+ "from upload_event ue "
					+ "where ue.status = 'success' "
					+ "and ue.s_id is not null "
					+ "and not ue.incomplete "
					+ "and ue.restore "
					+ "and not ue.results_db_applied ";
			pstmtLength = sd.prepareStatement(sqlLength);
			log.info("Get queue length: " + pstmtLength.toString());
			ResultSet rs = pstmtLength.executeQuery();
			if(rs.next()) {
				queue.length = rs.getInt(1);
			}

			String sqlProcessedRate = "select db_status, count(*) "
					+ "from upload_event ue "
					+ "where ue.status = 'success' "
					+ "and ue.s_id is not null "
					+ "and not ue.incomplete "
					+ "and ue.restore "
					+ "and ue.processed_time > now() - interval '1 minute' "
					+ "group by db_status";
			pstmtProcessedRate = sd.prepareStatement(sqlProcessedRate);

			rs = pstmtProcessedRate.executeQuery();
			while(rs.next()) {
				String status = rs.getString(1);
				queue.processed_rpm += rs.getInt(2);	// Processed updated for all status values
				if(status != null) {
					if(status.equals("error")) {
						queue.error_rpm = rs.getInt(2);
					}
				}
			}

			String sqlNewRate = "select count(*) "
					+ "from upload_event ue "
					+ "where ue.status = 'success' "
					+ "and ue.s_id is not null "
					+ "and not ue.incomplete "
					+ "and ue.restore "
					+ "and ue.upload_time > now() - interval '1 minute'";
			pstmtNewRate = sd.prepareStatement(sqlNewRate);

			rs = pstmtNewRate.executeQuery();
			if(rs.next()) {
				queue.new_rpm = rs.getInt(1);
			}


		} finally {
			try {if (pstmtLength != null) {pstmtLength.close();}} catch (SQLException e) {}
			try {if (pstmtProcessedRate != null) {pstmtProcessedRate.close();}} catch (SQLException e) {}
			try {if (pstmtNewRate != null) {pstmtNewRate.close();}} catch (SQLException e) {}
		}

		return queue;
	}
	
	/*
	 * Get status of s3upload queue
	 */
	public Queue getS3UploadQueueData(Connection sd) throws SQLException {

		PreparedStatement pstmtLength = null;
		PreparedStatement pstmtProcessedRate = null;
		PreparedStatement pstmtNewRate = null;

		Queue queue = new Queue();
		try {

			String sqlLength = "select count(*) "
					+ "from s3upload "
					+ "where status = 'new' ";
			pstmtLength = sd.prepareStatement(sqlLength);

			ResultSet rs = pstmtLength.executeQuery();
			if(rs.next()) {
				queue.length = rs.getInt(1);
			}

			String sqlProcessedRate = "select status, count(*) "
					+ "from s3upload "
					+ "where processed_time > now() - interval '1 minute' "
					+ "group by status";
			pstmtProcessedRate = sd.prepareStatement(sqlProcessedRate);

			rs = pstmtProcessedRate.executeQuery();
			while(rs.next()) {
				String status = rs.getString(1);
				if(status != null) {
					queue.processed_rpm += rs.getInt(2);	// processed applies for all status values
					if(status.equals("failed")) {
						queue.error_rpm = rs.getInt(2);
					}
				}
			}

			String sqlNewRate = "select count(*) "
					+ "from s3upload "
					+ "where created_time > now() - interval '1 minute' ";

			pstmtNewRate = sd.prepareStatement(sqlNewRate);

			rs = pstmtNewRate.executeQuery();
			if(rs.next()) {
				queue.new_rpm = rs.getInt(1);
			}


		} finally {
			try {if (pstmtLength != null) {pstmtLength.close();}} catch (SQLException e) {}
			try {if (pstmtProcessedRate != null) {pstmtProcessedRate.close();}} catch (SQLException e) {}
			try {if (pstmtNewRate != null) {pstmtNewRate.close();}} catch (SQLException e) {}
		}

		return queue;
	}

	/*
	 * Get status of s3upload queue
	 */
	public void getS3UploadQueueEvents(Connection sd, 
			ArrayList<QueueItem> items,
			int month,
			int year,
			String status,
			String tz) throws SQLException {

		PreparedStatement pstmt = null;

		try {

			boolean hasStatus = false;
			Timestamp t1 = GeneralUtilityMethods.getTimestampFromParts(year, month, 1);
			Timestamp t2 = GeneralUtilityMethods.getTimestampNextMonth(t1);
			
			String sql1 = "select id, filepath, status, o_id, "
					+ "is_media, created_time, processed_time, status, reason "
					+ "from s3upload ";		
			String sql2 = "where timezone(?, created_time) >=  ? "
					+ "and timezone(?, created_time) < ? ";
			String sql3 = "";
			if(status != null && !status.equals("any")) {
				sql3 = "and status = ? "; 
			}
			String sql4 = "order by id desc;";
			StringBuilder sql = new StringBuilder(sql1)
					.append(sql2)
					.append(sql3)
					.append(sql4);

			pstmt = sd.prepareStatement(sql.toString());
			int paramCount = 1;
			pstmt.setString(paramCount++, tz);
			pstmt.setTimestamp(paramCount++, t1);
			pstmt.setString(paramCount++, tz);
			pstmt.setTimestamp(paramCount++, t2);
			if(hasStatus) {
				pstmt.setString(paramCount++, status);
			}
			
			log.info("Queue: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()) {
				QueueItem item = new QueueItem();
				items.add(item);
				
				item.id = rs.getInt("id");
				item.filepath = rs.getString("filepath");
				item.oId = rs.getInt("o_id");
				item.media = rs.getBoolean("is_media");
				item.created_time = rs.getTimestamp("created_time");
				item.processed_time = rs.getTimestamp("processed_time");
				item.status = rs.getString("status");
				item.reason = rs.getString("reason");
			}


		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

	}

}
