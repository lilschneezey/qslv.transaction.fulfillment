package qslv.data;

import java.time.LocalDateTime;

public class OverdraftInstruction {
	private Account overdraftAccount;
	private String instructionLifecycleStatus;
	private LocalDateTime effectiveStart;
	private LocalDateTime effectiveEnd;

	public Account getOverdraftAccount() {
		return overdraftAccount;
	}
	public void setOverdraftAccount(Account overdraftAccount) {
		this.overdraftAccount = overdraftAccount;
	}
	public String getInstructionLifecycleStatus() {
		return instructionLifecycleStatus;
	}
	public void setInstructionLifecycleStatus(String instructionLifecycleStatus) {
		this.instructionLifecycleStatus = instructionLifecycleStatus;
	}
	public LocalDateTime getEffectiveStart() {
		return effectiveStart;
	}
	public void setEffectiveStart(LocalDateTime effectiveStart) {
		this.effectiveStart = effectiveStart;
	}
	public LocalDateTime getEffectiveEnd() {
		return effectiveEnd;
	}
	public void setEffectiveEnd(LocalDateTime effectiveEnd) {
		this.effectiveEnd = effectiveEnd;
	}

}
