package edu.cmu.sphinx.decoder;

import edu.cmu.sphinx.decoder.search.SearchManager;
import edu.cmu.sphinx.result.Result;
import edu.cmu.sphinx.util.props.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** An abstract decoder which implements all functionality which is indpendent of the used decoding-pardigm (pull/push). */
public abstract class AbstractDecoder implements ResultProducer, Configurable {

    /** The sphinx property name for the name of the search manager to use */
    @S4Component(type = SearchManager.class)
    public final static String PROP_SEARCH_MANAGER = "searchManager";
    protected SearchManager searchManager;

    @S4ComponentList(type = ResultListener.class)
    public static final String PROP_RESULT_LISTENERS = "resultListeners";
    protected final List<ResultListener> resultListeners = new ArrayList<ResultListener>();

    /** If set to true the used search-manager will be automatically allocated in <code>newProperties()</code>. */
    @S4Boolean(defaultValue = false)
    public static final String AUTO_ALLOCATE = "autoAllocate";

    /**
     * If set to <code>false</code> the used search-manager all registered result listeners will be notified only for
     * final results. Per default non-final results don't trigger notification, because in most application the
     * utterance final result will be sufficient.
     */
    @S4Boolean(defaultValue = false)
    public static final String FIRE_NON_FINAL_RESULTS = "fireNonFinalResults";
    private boolean fireNonFinalResults;

    private String name;
    protected Logger logger;

    public AbstractDecoder() {
    }

    /**
     *
     * @param searchManager
     * @param fireNonFinalResults
     * @param autoAllocate
     * @param resultListeners
     */
    public AbstractDecoder(SearchManager searchManager, boolean fireNonFinalResults, boolean autoAllocate, List<? extends Configurable> resultListeners) {
        String name = getClass().getName();
             init( name, Logger.getLogger(name),
                   searchManager, fireNonFinalResults, autoAllocate, resultListeners);        
    }

    /**
     * Decode frames until recognition is complete
     *
     * @param referenceText the reference text (or null)
     * @return a result
     */
    public abstract Result decode(String referenceText);

    public void newProperties(PropertySheet ps) throws PropertyException {
        init( ps.getInstanceName(), ps.getLogger(), (SearchManager) ps.getComponent(PROP_SEARCH_MANAGER), ps.getBoolean(FIRE_NON_FINAL_RESULTS), ps.getBoolean(AUTO_ALLOCATE), ps.getComponentList(PROP_RESULT_LISTENERS));
    }

    private void init(String name, Logger logger, SearchManager searchManager, boolean fireNonFinalResults, boolean autoAllocate, List<? extends Configurable> listeners) {
        this.name = name;
        this.logger = logger;

        this.searchManager = searchManager;
        this.fireNonFinalResults = fireNonFinalResults;

        if (autoAllocate) {
            searchManager.allocate();
        }

        for (Configurable configurable : listeners) {
            addResultListener((ResultListener) configurable);
        }
    }


    /** Allocate resources necessary for decoding */
    public void allocate() {
        searchManager.allocate();
    }


    /** Deallocate resources */
    public void deallocate() {
        searchManager.deallocate();
    }


    /**
     * Adds a result listener to this recognizer. A result listener is called whenever a new result is generated by the
     * recognizer. This method can be called in any state.
     *
     * @param resultListener the listener to add
     */
    public void addResultListener(ResultListener resultListener) {
        resultListeners.add(resultListener);
    }


    /**
     * Removes a previously added result listener. This method can be called in any state.
     *
     * @param resultListener the listener to remove
     */
    public void removeResultListener(ResultListener resultListener) {
        resultListeners.remove(resultListener);
    }


    /**
     * Fires new results as soon as they become available.
     *
     * @param result the new result
     */
    protected void fireResultListeners(Result result) {
        if (fireNonFinalResults || result.isFinal()) {
            for (ResultListener resultListener : resultListeners) {
                resultListener.newResult(result);
            }
        }else {
            logger.finer("skipping non-final result " + result);
        }
    }


    public String toString() {
        return name;
    }
}
