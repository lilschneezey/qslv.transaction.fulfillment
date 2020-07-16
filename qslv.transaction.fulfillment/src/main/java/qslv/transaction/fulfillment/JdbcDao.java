package qslv.transaction.fulfillment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import qslv.data.Account;
import qslv.data.OverdraftInstruction;

@Repository
public class JdbcDao {
	private static final Logger log = LoggerFactory.getLogger(JdbcDao.class);

	@Autowired(required = false)
	private JdbcTemplate jdbcTemplate;

	public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}
	
	public final static String getOverdraftInstructions_sql = 
			"SELECT o.overdraft_account_no, oda.lifecycle_status_cd as od_lifecycle_status, o.lifecycle_status_cd, o.effective_start_dt, o.effective_end_dt"
			+ " FROM overdraft_instruction o, account oda"
			+ " WHERE o.account_no = ?"
			+ " AND o.overdraft_account_no = oda.account_no;";
	
	public List<OverdraftInstruction> getOverdraftInstructions(final String accountNumber) {
		log.warn("getOverdraftInstructions ENTRY {}", accountNumber);

		List<OverdraftInstruction> resources = jdbcTemplate.query(getOverdraftInstructions_sql,
				new RowMapper<OverdraftInstruction>() {
					public OverdraftInstruction mapRow(ResultSet rs, int rowNum) throws SQLException {
						OverdraftInstruction res = new OverdraftInstruction();
						res.setOverdraftAccount(new Account());
						
						res.getOverdraftAccount().setAccountNumber(rs.getString(1));
						res.getOverdraftAccount().setAccountLifeCycleStatus(rs.getString(2));
						res.setInstructionLifecycleStatus(rs.getString(3));
						res.setEffectiveStart(rs.getDate(4).toLocalDate().atStartOfDay() );
						res.setEffectiveEnd(rs.getDate(5) == null ? null :rs.getDate(5).toLocalDate().atStartOfDay());
						return res;
					}
				}, accountNumber);
		

		log.warn("getOverdraftInstructions size {}", resources.size());
		return resources;
	}
}