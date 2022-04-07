package org.smap.sdal.model;

import java.sql.Date;
import java.sql.Timestamp;

public class AR {
	public String userIdent;
	public String userName;
	public int p_id;
	public String project;
	public String survey;
	public int s_id;
	public String device;
	public Date created;
	public int usageInPeriod;
	public int allTimeUsage;
	public Timestamp firstRefresh;
	public Timestamp lastRefresh;
	public String duration;
}
