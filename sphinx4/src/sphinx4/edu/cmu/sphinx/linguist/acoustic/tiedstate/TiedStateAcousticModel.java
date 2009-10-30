/*
 * Copyright 1999-2002 Carnegie Mellon University.  
 * Portions Copyright 2002 Sun Microsystems, Inc.  
 * Portions Copyright 2002 Mitsubishi Electric Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */

package edu.cmu.sphinx.linguist.acoustic.tiedstate;

// Placeholder for a package import

import edu.cmu.sphinx.linguist.acoustic.*;
import edu.cmu.sphinx.util.Timer;
import edu.cmu.sphinx.util.TimerPool;
import edu.cmu.sphinx.util.props.*;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads a tied-state acoustic model generated by the Sphinx-3 trainer.
 * <p/>
 * <p/>
 * It is not the goal of this documentation to provide an explanation about the concept of HMMs. The explanation below
 * is superficial, and provided only in a way that the files in the acoustic model package make sense.
 * <p/>
 * An HMM models a process using a sequence of states. Associated with each state, there is a probability density
 * function. A popular choice for this function is a Gaussian mixture, that is, a summation of Gaussians. As you may
 * recall, a single Gaussian is defined by a mean and a variance, or, in the case of a multidimensional Gaussian, by a
 * mean vector and a covariance matrix, or, under some simplifying assumptions, a variance vector. The "means" and
 * "variances" files in the "continuous" directory contain exactly this: a table in which each line contains a mean
 * vector or a variance vector respectively. The dimension of these vectors is the same as the incoming data, the
 * encoded speech signal. The Gaussian mixture is a summation of Gaussians, with different weights for different
 * Gaussians. The "mixture_weights" file contains this: each line contains the weights for a combination of Gaussians.
 * <p/>
 * The HMM is a model with a set of states. The transitions between states have an associated probability. These
 * probabilities make up the transition matrices stored in the "transition_matrices" file.
 * <p/>
 * The files in the "continuous" directory are, therefore, tables, or pools, of means, variances, mixture weights, and
 * transition probabilities.
 * <p/>
 * The dictionary is a file that maps words to their phonetic transcriptions, that is, it maps words to sequences of
 * phonemes.
 * <p/>
 * The language model contains information about probabilities of words in a language. These probabilities could be for
 * individual words or for sequences of two or three words.
 * <p/>
 * The model definition file in a way ties everything together. If the recognition system models phonemes, there is an
 * HMM for each phoneme. The model definition file has one line for each phoneme. The phoneme could be in a context
 * dependent or independent. Each line, therefore, identifies a unique HMM. This line has the phoneme identification,
 * the non-required left or right context, the index of a transition matrix, and, for each state, the index of a mean
 * vector, a variance vector, and a set of mixture weights.
 */
@SuppressWarnings({"UnnecessaryLocalVariable", "JavaDoc", "JavaDoc", "JavaDoc"})
public class TiedStateAcousticModel implements AcousticModel {

    /** The property that defines the component used to load the acoustic model */
    @S4Component(type = Loader.class)
    public final static String PROP_LOADER = "loader";

    /** The property that defines the unit manager */
    @S4Component(type = UnitManager.class)
    public final static String PROP_UNIT_MANAGER = "unitManager";

    /** Controls whether we generate composites or CI units when no context is given during a lookup. */
    @S4Boolean(defaultValue = true)
    public final static String PROP_USE_COMPOSITES = "useComposites";

    /** Model load timer */
    protected final static String TIMER_LOAD = "AM_Load";


    // -----------------------------
    // Configured variables
    // -----------------------------
    protected String name;
    protected Logger logger;
    protected Loader loader;
    protected UnitManager unitManager;
    private boolean useComposites;
    private Properties properties;

    // ----------------------------
    // internal variables
    // -----------------------------
    transient protected Timer loadTimer;
    final transient private Map<String, SenoneSequence> compositeSenoneSequenceCache = new HashMap<String, SenoneSequence>();
    private boolean allocated;

    public TiedStateAcousticModel( Loader loader, UnitManager unitManager, boolean useComposites) {
        this.loader = loader;
        this.unitManager = unitManager;
        this.useComposites = useComposites;
        this.logger = Logger.getLogger(getClass().getName());
    }

    public TiedStateAcousticModel() {

    }

    public void newProperties(PropertySheet ps) throws PropertyException {
        loader = (Loader) ps.getComponent(PROP_LOADER);
        unitManager = (UnitManager) ps.getComponent(PROP_UNIT_MANAGER);
        useComposites = ps.getBoolean(PROP_USE_COMPOSITES);
        logger = ps.getLogger();
    }

    /**
     * initialize this acoustic model with the given name and context.
     *
     * @throws IOException if the model could not be loaded
     */
    @Override
    public void allocate() throws IOException {
        if (!allocated) {
            this.loadTimer = TimerPool.getTimer(this, TIMER_LOAD);
            loadTimer.start();
            loader.load();
            loadTimer.stop();
            logInfo();
            allocated = true;
        }
    }


    /* (non-Javadoc)
    * @see edu.cmu.sphinx.linguist.acoustic.AcousticModel#deallocate()
    */
    @Override
    public void deallocate() {
    }


    /**
     * Returns the name of this AcousticModel, or null if it has no name.
     *
     * @return the name of this AcousticModel, or null if it has no name
     */
    @Override
    public String getName() {
        return name;
    }


    /**
     * Gets a composite HMM for the given unit and context
     *
     * @param unit     the unit for the hmm
     * @param position the position of the unit within the word
     * @return a composite HMM
     */
    private HMM getCompositeHMM(Unit unit, HMMPosition position) {


        if (true) { // use a true composite
            Unit ciUnit = unitManager.getUnit(unit.getName(),
                    unit.isFiller(), Context.EMPTY_CONTEXT);

            SenoneSequence compositeSequence =
                    getCompositeSenoneSequence(unit, position);

            SenoneHMM contextIndependentHMM = (SenoneHMM)
                    lookupNearestHMM(ciUnit,
                            HMMPosition.UNDEFINED, true);
            float[][] tmat = contextIndependentHMM.getTransitionMatrix();
            return new SenoneHMM(unit, compositeSequence, tmat, position);
        } else { // BUG: just a test. use CI units instead of composites
            Unit ciUnit = lookupUnit(unit.getName());

            assert unit.isContextDependent();
            if (ciUnit == null) {
                logger.severe("Can't find HMM for " + unit.getName());
            }
            assert ciUnit != null;
            assert !ciUnit.isContextDependent();

            HMMManager mgr = loader.getHMMManager();
            HMM hmm = mgr.get(HMMPosition.UNDEFINED, ciUnit);
            return hmm;
        }
    }


    /**
     * Given a unit, returns the HMM that best matches the given unit. If exactMatch is false and an exact match is not
     * found, then different word positions are used. If any of the contexts are non-silence filler units. a silence
     * filler unit is tried instead.
     *
     * @param unit       the unit of interest
     * @param position   the position of the unit of interest
     * @param exactMatch if true, only an exact match is acceptable.
     * @return the HMM that best matches, or null if no match could be found.
     */
    @Override
    public HMM lookupNearestHMM(Unit unit, HMMPosition position,
                                boolean exactMatch) {

        if (exactMatch) {
            return lookupHMM(unit, position);
        } else {
            HMMManager mgr = loader.getHMMManager();
            HMM hmm = mgr.get(position, unit);

            if (hmm != null) {
                return hmm;
            }
            // no match, try a composite

            if (useComposites && hmm == null) {
                if (isComposite(unit)) {

                    hmm = getCompositeHMM(unit, position);
                    if (hmm != null) {
                        mgr.put(hmm);
                    }
                }
            }
            // no match, try at other positions
            if (hmm == null) {
                hmm = getHMMAtAnyPosition(unit);
            }
            // still no match, try different filler
            if (hmm == null) {
                hmm = getHMMInSilenceContext(unit, position);
            }

            // still no match, backoff to base phone
            if (hmm == null) {
                Unit ciUnit = lookupUnit(unit.getName());

                assert unit.isContextDependent();
                if (ciUnit == null) {
                    logger.severe("Can't find HMM for " + unit.getName());
                }
                assert ciUnit != null;
                assert !ciUnit.isContextDependent();

                hmm = mgr.get(HMMPosition.UNDEFINED, ciUnit);
            }

            assert hmm != null;

            // System.out.println("PROX match for "
            // 	+ unit + " at " + position + ":" + hmm);

            return hmm;
        }
    }


    /**
     * Determines if a unit is a composite unit
     *
     * @param unit the unit to test
     * @return true if the unit is missing a right context
     */
    private boolean isComposite(Unit unit) {

        if (unit.isFiller()) {
            return false;
        }

        Context context = unit.getContext();
        if (context instanceof LeftRightContext) {
            LeftRightContext lrContext = (LeftRightContext) context;
            if (lrContext.getRightContext() == null) {
                return true;
            }
            if (lrContext.getLeftContext() == null) {
                return true;
            }
        }
        return false;
    }


    /**
     * Looks up the context independent unit given the name
     *
     * @param name the name of the unit
     * @return the unit or null if the unit was not found
     */
    private Unit lookupUnit(String name) {
        return loader.getContextIndependentUnits().get(name);
    }


    /**
     * Returns an iterator that can be used to iterate through all the HMMs of the acoustic model
     *
     * @return an iterator that can be used to iterate through all HMMs in the model. The iterator returns objects of
     *         type <code>HMM</code>.
     */
    @Override
    public Iterator<HMM> getHMMIterator() {
        return loader.getHMMManager().iterator();
    }


    /**
     * Returns an iterator that can be used to iterate through all the CI units in the acoustic model
     *
     * @return an iterator that can be used to iterate through all CI units. The iterator returns objects of type
     *         <code>Unit</code>
     */
    @Override
    public Iterator<Unit> getContextIndependentUnitIterator() {
        return loader.getContextIndependentUnits().values().iterator();
    }


    /**
     * Get a composite senone sequence given the unit The unit should have a LeftRightContext, where one or two of
     * 'left' or 'right' may be null to indicate that the match should succeed on any context.
     *
     * @param unit the unit
     * @param position
     * @return
     */
    public SenoneSequence getCompositeSenoneSequence(Unit unit,
                                                     HMMPosition position) {
        Context context = unit.getContext();
        SenoneSequence compositeSenoneSequence = null;
        compositeSenoneSequence = compositeSenoneSequenceCache.get(unit.toString());

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("getCompositeSenoneSequence: " + unit + (compositeSenoneSequence == null ? "" : "Cached"));
        }
        if (compositeSenoneSequence != null) {
            return compositeSenoneSequence;
        }

        // Iterate through all HMMs looking for
        // a) An hmm with a unit that has the proper base
        // b) matches the non-null context

        List<SenoneSequence> senoneSequenceList = new ArrayList<SenoneSequence>();

        // collect all senone sequences that match the pattern
        for (Iterator<HMM> i = getHMMIterator(); i.hasNext();) {
            SenoneHMM hmm = (SenoneHMM) i.next();
            if (hmm.getPosition() == position) {
                Unit hmmUnit = hmm.getUnit();
                if (hmmUnit.isPartialMatch(unit.getName(), context)) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("collected: " + hmm.getUnit());
                    }
                    senoneSequenceList.add(hmm.getSenoneSequence());
                }
            }
        }

        // couldn't find any matches, so at least include the CI unit
        if (senoneSequenceList.isEmpty()) {
            Unit ciUnit = unitManager.getUnit(unit.getName(), unit.isFiller());
            SenoneHMM baseHMM = lookupHMM(ciUnit, HMMPosition.UNDEFINED);
            senoneSequenceList.add(baseHMM.getSenoneSequence());
        }

        // Add this point we have all of the senone sequences that
        // match the base/context pattern collected into the list.
        // Next we build a CompositeSenone consisting of all of the
        // senones in each position of the list.

        // First find the longest senone sequence

        int longestSequence = 0;
        for (SenoneSequence ss : senoneSequenceList) {
            if (ss.getSenones().length > longestSequence) {
                longestSequence = ss.getSenones().length;
            }
        }

        // now collect all of the senones at each position into
        // arrays so we can create CompositeSenones from them
        // QUESTION: is is possible to have different size senone
        // sequences. For now lets assume the worst case.

        List<CompositeSenone> compositeSenones = new ArrayList<CompositeSenone>();
        float logWeight = 0.0f;
        for (int i = 0; i < longestSequence; i++) {
            Set<Senone> compositeSenoneSet = new HashSet<Senone>();
            for (SenoneSequence senoneSequence : senoneSequenceList) {
                if (i < senoneSequence.getSenones().length) {
                    Senone senone = senoneSequence.getSenones()[i];
                    compositeSenoneSet.add(senone);
                }
            }
            compositeSenones.add(CompositeSenone.create(
                    compositeSenoneSet, logWeight));
        }

        compositeSenoneSequence = SenoneSequence.create(compositeSenones);
        compositeSenoneSequenceCache.put(unit.toString(),
                compositeSenoneSequence);

        if (logger.isLoggable(Level.FINE)) {
            logger.fine(unit + " consists of " + compositeSenones.size() + " composite senones");
            if (logger.isLoggable(Level.FINEST)) {
                compositeSenoneSequence.dump("am");
            }
        }
        return compositeSenoneSequence;
    }


    /**
     * Returns the size of the left context for context dependent units
     *
     * @return the left context size
     */
    @Override
    public int getLeftContextSize() {
        return loader.getLeftContextSize();
    }


    /**
     * Returns the size of the right context for context dependent units
     *
     * @return the left context size
     */
    @Override
    public int getRightContextSize() {
        return loader.getRightContextSize();
    }


    /**
     * Given a unit, returns the HMM that exactly matches the given unit.
     *
     * @param unit     the unit of interest
     * @param position the position of the unit of interest
     * @return the HMM that exactly matches, or null if no match could be found.
     */
    private SenoneHMM lookupHMM(Unit unit, HMMPosition position) {
        return (SenoneHMM) loader.getHMMManager().get(position, unit);
    }


    /**
     * Creates a string useful for tagging a composite senone sequence
     *
     * @param base    the base unit
     * @param context the context
     * @return the tag associated with the composite senone sequence
     */
    private String makeTag(Unit base, Context context) {
        return '(' + base.getName() + '-' + context + ')';
    }


    /** Dumps information about this model to the logger */
    protected void logInfo() {
        if (loader != null) {
            loader.logInfo();
        }
        logger.info("CompositeSenoneSequences: " +
                compositeSenoneSequenceCache.size());
    }


    /**
     * Searches an hmm at any position
     *
     * @param unit the unit to search for
     * @return hmm the hmm or null if it was not found
     */
    private SenoneHMM getHMMAtAnyPosition(Unit unit) {
        HMMManager mgr = loader.getHMMManager();
        for (HMMPosition pos : HMMPosition.values()) {
            SenoneHMM hmm = (SenoneHMM)mgr.get(pos, unit);
            if (hmm != null)
                return hmm;
        }
        return null;
    }


    /**
     * Given a unit, search for the HMM associated with this unit by replacing all non-silence filler contexts with the
     * silence filler context
     *
     * @param unit the unit of interest
     * @param position
     * @return the associated hmm or null
     */
    private SenoneHMM getHMMInSilenceContext(Unit unit, HMMPosition position) {
        SenoneHMM hmm = null;
        HMMManager mgr = loader.getHMMManager();
        Context context = unit.getContext();

        if (context instanceof LeftRightContext) {
            LeftRightContext lrContext = (LeftRightContext) context;

            Unit[] lc = lrContext.getLeftContext();
            Unit[] rc = lrContext.getRightContext();

            Unit[] nlc;
            Unit[] nrc;

            if (hasNonSilenceFiller(lc)) {
                nlc = replaceNonSilenceFillerWithSilence(lc);
            } else {
                nlc = lc;
            }

            if (hasNonSilenceFiller(rc)) {
                nrc = replaceNonSilenceFillerWithSilence(rc);
            } else {
                nrc = rc;
            }

            if (nlc != lc || nrc != rc) {
                Context newContext = LeftRightContext.get(nlc, nrc);
                Unit newUnit = unitManager.getUnit(unit.getName(),
                        unit.isFiller(), newContext);
                hmm = (SenoneHMM) mgr.get(position, newUnit);
                if (hmm == null) {
                    hmm = getHMMAtAnyPosition(newUnit);
                }
            }
        }
        return hmm;
    }


    /**
     * Some debugging code that looks for illformed contexts
     *
     * @param msg the message associated with the check
     * @param c   the context to check
     */
    private void checkNull(String msg, Unit[] c) {
        for (int i = 0; i < c.length; i++) {
            if (c[i] == null) {
                System.out.println("null at index " + i + " of " + msg);
            }
        }
    }


    /**
     * Returns true if the array of units contains a non-silence filler
     *
     * @param units the units to check
     * @return true if the array contains a filler that is not the silence filler
     */
    private boolean hasNonSilenceFiller(Unit[] units) {
        if (units == null) {
            return false;
        }

        for (Unit unit : units) {
            if (unit.isFiller() &&
                !unit.equals(UnitManager.SILENCE)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Returns a unit array with all non-silence filler units replaced with the silence filler a non-silence filler
     *
     * @param context the context to check
     * @return true if the array contains a filler that is not the silence filler
     */
    private Unit[] replaceNonSilenceFillerWithSilence(Unit[] context) {
        Unit[] replacementContext = new Unit[context.length];
        for (int i = 0; i < context.length; i++) {
            if (context[i].isFiller() &&
                    !context[i].equals(UnitManager.SILENCE)) {
                replacementContext[i] = UnitManager.SILENCE;
            } else {
                replacementContext[i] = context[i];
            }
        }
        return replacementContext;
    }


    /**
     * Returns the properties of this acoustic model.
     *
     * @return the properties of this acoustic model
     */
    @Override
    public Properties getProperties() {
        if (properties == null) {
            properties = new Properties();
            try {
                properties.load
                        (getClass().getResource("model.props").openStream());
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        return properties;
    }
}
