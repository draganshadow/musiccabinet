package com.github.hakko.musiccabinet.service.lastfm;

import java.util.Locale;

import com.github.hakko.musiccabinet.domain.model.aggr.SearchIndexUpdateProgress;
import com.github.hakko.musiccabinet.exception.ApplicationException;

/*
 * Common base class for services updating database
 * (adding artist relations, artist metadata, artist top tracks etc).
 * 
 * This allows us to invoke them all in a unified way, and ask for progress
 * meanwhile the update is running.
 */
public abstract class SearchIndexUpdateService {

	private SearchIndexUpdateProgress progress;
	
	protected Locale locale;
	
	public SearchIndexUpdateService() {
		progress = new SearchIndexUpdateProgress(getUpdateDescription());
		locale = Locale.ENGLISH;
	}
	
	/*
	 * Reset progress state.
	 */
	public final void reset() {
		progress.reset();
	}
	
	/*
	 * Returns current progress state, null if not yet started.
	 */
	public final SearchIndexUpdateProgress getProgress() {
		return progress;
	}
	
	/*
	 * Set total number of operations this update service intends to do.
	 */
	protected void setTotalOperations(int totalOperations) {
		progress.setTotalOperations(totalOperations);
	}

	/*
	 * Set number of finished operations for this update service.
	 */
	protected void addFinishedOperation() {
		progress.addFinishedOperation();
	}
	
	/*
	 * Returns the kind of object this service updates, meant to be read by end user.
	 */
	public abstract String getUpdateDescription();
	
	/*
	 * Each update service is supposed to implement their own business logic here.
	 */
	protected abstract void updateSearchIndex() throws ApplicationException;

	// Spring setter(s)
	public Locale getLocale() {
		return locale;
	}

	public void setLocale(Locale locale) {
		this.locale = locale;
	}
}